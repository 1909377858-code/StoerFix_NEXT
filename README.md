# SoterFix

## 严重警告

任何形式的烧录TEE都有可能导致TEE真死，在做出选择时不要让自己后悔！

作者自测一加13T+sukisu环境可用，其他方案不做保证。

回锁后原厂密钥状态没测过，风险未知。

## 模块简介

烧录TEE法过三角洲检测。本模块帮你自动完成TEE烧录及维护。

- 每3分钟进行一次TEE检测，掉了自动刷入
- 开机时进行5次尝试修复StoreKey

SoterFix 模块用于 **自动烧录 TEE**（当无法获取设备认证时）、**自动修复 Keystore Key**（开机或烧录 TEE 后），并自动 **检测 Key 状态**。


## 强兼TrickyStore

如果有必须使用TrickyStore的场景，你需要让TrickyStore对idlike.kac（KA cheeker）不生效，这样才能使用自动烧录 TEE功能。

---

## 安装步骤

1. 将模块文件放入 Magisk Modules 目录：  
