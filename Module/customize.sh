#!/system/bin/sh

MODDIR="/data/adb/modules_update/SoterFix/"
KEY_FILE="/data/user/0/idlike.kac/files/key_state"

ui_print "******************************"
ui_print "SOTER Key一键修复模块"
ui_print "注意：本模块会烧录key，除了某些特定设备外不建议烧录。"
ui_print "版本: 1.0"
ui_print "作者: IDlike"
ui_print "经测试/建议的su：sukisu"
ui_print "经测试/必须的设备：一加13T"
ui_print "* 与TrickyStore 不兼容，请卸载TrickyStore模块。"
ui_print "* TrickyStore会让APP无法读取密钥状态。"
ui_print "******************************"
ui_print ""
ui_print ""

ui_print "正在安装检测APP..."

ui_print ""
ui_print ""

pm install $MODDIR/app-release.apk
ui_print "安装完成，检查功能..."

am start-foreground-service -n idlike.kac/.kc
sleep 3
# 判断文件是否存在
if [ -f "$KEY_FILE" ]; then
  CONTENT=$(cat "$KEY_FILE")
      if [ "$CONTENT" = "true" ]; then
          echo "key状态正常 （此检测不影响模块安装）"
      elif [ "$CONTENT" = "false" ]; then
          echo "key状态异常 （此检测不影响模块安装）"
      else
          echo "失败！"
          abort "APP 安装失败，模块没有被安装。"
      fi
else
     abort "测试失败，模块没有被安装。"

fi


chmod 0755 $MODDIR/service.sh
chmod 0755 $MODDIR/action.sh
chmod 777 $MODDIR/attestation

ui_print "正在烧录key..."    

LD_LIBRARY_PATH=/vendor/lib64/hw /vendor/bin/KmInstallKeybox $MODDIR/attestation attestation true

ui_print "烧录完成，设备需要重启以完成更改。"

