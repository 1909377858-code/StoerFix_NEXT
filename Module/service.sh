#!/system/bin/sh
MODDIR="/data/adb/modules/SoterFix/"

KEY_FILE="/data/user/0/idlike.kac/files/key_state"


# 等待系统启动完成
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 5
done


# 循环执行 5 次，每次间隔 1 分钟
for i in $(seq 1 5); do
    echo "$(date) - 第 $i 次执行"
    $MODDIR/action.sh
    if [ $i -lt 5 ]; then
        sleep 60  # 1分钟 = 60秒
    fi
done

while true; do
    am start-foreground-service -n idlike.kac/.kc
    sleep 3
    # 判断文件是否存在
    if [ -f "$KEY_FILE" ]; then
      CONTENT=$(cat "$KEY_FILE")
          if [ "$CONTENT" = "true" ]; then
              echo "key状态正常，尝试修复SOTER KEY"
          elif [ "$CONTENT" = "false" ]; then
              echo "key状态异常，尝试烧录key"
              echo "正在烧录key..."
              LD_LIBRARY_PATH=/vendor/lib64/hw /vendor/bin/KmInstallKeybox $MODDIR/attestation attestation true
          else
              echo "错误！检查app是否被卸载。"
          fi
    fi
    sleep 180  # 3分钟 = 180秒
done