package idlike.kac

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ResultReceiver
import android.security.KeyPairGeneratorSpec
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.security.auth.x500.X500Principal


class kc : Service(){

    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_RECEIVER = "EXTRA_RECEIVER"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Hello from Service"
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RECEIVER)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Key Cheek is working...")
            .setContentText("please wait")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)

            CoroutineScope(Dispatchers.IO).launch {
                val overall = runFullAttestationCheck()
                saveOverallToInternalStorage(applicationContext, overall)
                stopForeground(true)
                stopSelf()
            }

        return START_NOT_STICKY
    }

    fun saveOverallToInternalStorage(context: Context, overall: Boolean) {
        try {
            // 获取应用内部目录
            val internalDir: File = context.filesDir

            // 文件名
            val file = File(internalDir, "key_state")

            // 将 Boolean 转成字符串写入文件
            FileOutputStream(file).use { fos ->
                fos.write(overall.toString().toByteArray())
                fos.flush()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }




    private data class SingleResult(
        val ok: Boolean,
        val deviceIdsUnavailable: Boolean,
        val error: Throwable?
    )

    /**
     * Run both checks (strongbox and default). If any one fails -> return false; else true.
     */
    private suspend fun runFullAttestationCheck(): Boolean {
        // Try without StrongBox first (to avoid failing on devices that don't support it)
        val modes = listOf(false, true) // false = not using security module; true = using security module
        val results = modes.map { useStrongBox ->
            try {
                testAttestationMode(useStrongBox)
            } catch (t: Throwable) {
                // Shouldn't happen because testAttestationMode catches expected exceptions,
                // but keep safety.
                SingleResult(false, isDeviceIdUnavailable(t), t)
            }
        }

        // Log details
        results.forEachIndexed { idx, r ->
            val mode = if (modes[idx]) "StrongBox" else "Default"
            //Log.i(TAG, "Mode=$mode ok=${r.ok} deviceIdsUnavailable=${r.deviceIdsUnavailable} err=${r.error}")
        }

        // If any unsuccessful -> false
        return results.all { it.ok && !it.deviceIdsUnavailable }
    }

    /**
     * Test one mode:
     * - generate attested keypair (in AndroidKeyStore) with attestationChallenge
     * - obtain cert chain
     * - verify signatures / validity (no revocation)
     * - cleanup created alias
     */
    private fun testAttestationMode(useStrongBox: Boolean): SingleResult {
        val alias = "demo_attest_${if (useStrongBox) "strongbox" else "default"}_${UUID.randomUUID()}"
        try {
            // Generate keypair with attestation request
            generateAttestedKeyPair(alias, useStrongBox)

            // Load chain
            val chain = getCertificateChain(alias)
                ?: throw ProviderException("No certificate chain returned for alias $alias")

            // Verify chain (signature and validity)
            verifyCertificateChain(chain)

            // Success
            return SingleResult(true, false, null)
        } catch (e: Throwable) {
            //Log.w(TAG, "testAttestationMode($useStrongBox) failed", e)
            val deviceIdsUnavailable = isDeviceIdUnavailable(e)
            return SingleResult(false, deviceIdsUnavailable, e)
        } finally {
            // Best-effort cleanup
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                if (ks.containsAlias(alias)) {
                    ks.deleteEntry(alias)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Generate a keypair in AndroidKeyStore requesting attestation.
     * This will request attestation certificates to be created by the keystore provider.
     */
    private fun generateAttestedKeyPair(alias: String, useStrongBox: Boolean) {
        // Use EC P-256 for broad compatibility
        val kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // KeyGenParameterSpec.Builder is available from M
            val specBuilder = android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                // Request attestation by providing a challenge (can be any bytes)
                .setAttestationChallenge("attest-challenge-demo".toByteArray(Charsets.UTF_8))
                // mark key non-extractable and not for encryption
                .setUserAuthenticationRequired(false)

            // setIsStrongBoxBacked is available on API 28+
            try {
                specBuilder.setIsStrongBoxBacked(useStrongBox)
            } catch (t: Throwable) {
                // Some environments may throw if provider doesn't support property - ignore and let generation fail/catch later
                //Log.w(TAG, "setIsStrongBoxBacked not accepted: ${t.message}")
            }

            specBuilder.build()
        } else {
            // Very old API - fall back to KeyPairGeneratorSpec (no attestation)
            val start = Calendar.getInstance()
            val end = Calendar.getInstance()
            end.add(Calendar.YEAR, 25)
            KeyPairGeneratorSpec.Builder(this)
                .setAlias(alias)
                .setSubject(X500Principal("CN=$alias"))
                .setSerialNumber(BigInteger.valueOf(1))
                .setStartDate(start.time)
                .setEndDate(end.time)
                .build()
        }

        kpg.initialize(builder)
        // This may throw ProviderException, StrongBoxUnavailableException, Provider-specific exceptions
        kpg.generateKeyPair()
    }

    /**
     * Read certificate chain from AndroidKeyStore for alias.
     */
    private fun getCertificateChain(alias: String): List<X509Certificate>? {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val certs: Array<Certificate>? = ks.getCertificateChain(alias)
        if (certs == null) return null
        val x509s = certs.map { it as X509Certificate }
        return x509s
    }

    /**
     * Verify certificate chain:
     * - For each cert from leaf to root, verify signature by parent public key
     * - Check validity dates (notBefore / notAfter)
     *
     * Throws CertificateException / GeneralSecurityException when verification fails.
     */
    @Throws(GeneralSecurityException::class, CertificateException::class)
    private fun verifyCertificateChain(chain: List<X509Certificate>) {
        if (chain.isEmpty()) throw CertificateException("Empty certificate chain")
        // Parent is the next cert in chain; last cert is root
        for (i in 0 until chain.size - 1) {
            val cert = chain[i]
            val parent = chain[i + 1]
            // verify signature
            cert.verify(parent.publicKey)
            // check validity
            cert.checkValidity()
        }
        // check root validity (expiry)
        chain.last().checkValidity()
    }

    /**
     * Heuristic to detect "unable to attest device IDs" from exceptions.
     * Mirrors repository behavior: recognizes DeviceIdAttestationException, KeyStoreException numeric code,
     * or text containing "device ids".
     */
    private fun isDeviceIdUnavailable(t: Throwable?): Boolean {
        if (t == null) return false
        // 1) Direct class name match (some platform classes may not be accessible at compile time)
        var cur: Throwable? = t
        while (cur != null) {
            val className = cur::class.java.name
            if (className.contains("DeviceIdAttestationException") || className.contains("DeviceIdAttestation")) {
                return true
            }
            // 2) On Android 13+ (Tiramisu) KeyStoreException may have numeric error code ERROR_ID_ATTESTATION_FAILURE.
            // Try via reflection to call getNumericErrorCode() if present.
            try {
                val cls = cur::class.java
                // method name getNumericErrorCode
                val method = try {
                    cls.getMethod("getNumericErrorCode")
                } catch (e: NoSuchMethodException) {
                    null
                }
                if (method != null) {
                    val codeObj = method.invoke(cur)
                    if (codeObj is Number) {
                        val code = codeObj.toInt()
                        // From platform: ERROR_ID_ATTESTATION_FAILURE is 6 (platform internal), but can't be 100% sure.
                        // We'll consider code value 6 or any documented id-attestation code; defensively check typical constant names via reflection too.
                        if (code == 6) {
                            return true
                        }
                    }
                }
            } catch (ignored: Exception) {
            }

            // 3) Message contains "device ids" (fallback used in repository)
            val msg = cur.message
            if (msg != null && msg.lowercase(Locale.ROOT).contains("device id")) return true
            if (msg != null && msg.lowercase(Locale.ROOT).contains("device ids")) return true

            cur = cur.cause
        }
        return false
    }
}