#!/system/bin/sh

MODDIR="/data/adb/modules/SoterFix/"

KEY_FILE="/data/user/0/idlike.kac/files/key_state"

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
setenforce 0
echo "开始修复SOTER Key问题"
stop vendor.soter
sleep 3
pm clear com.tencent.soter.soterserver
start vendor.soter
sleep 5
getprop init.svc.vendor.soter
echo "修复完成，请开机后在拨号输入*#899＃选手动测试查看SOTER Key，若失败则多刷新几次"
setenforce 1

