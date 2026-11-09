# 签名密钥说明

本目录存放 Android 发布签名文件，`.jks` 已在 `.gitignore` 中排除，**不会提交到版本库**。

## 当前工程内置的演示密钥

| 项 | 值 |
|---|---|
| 文件 | `powerline-release.jks` |
| 别名 | `powerline` |
| 密钥库口令 | `powerline2024` |
| 密钥口令 | `powerline2024` |
| 有效期 | 30 年（10950 天） |
| 主题 | 见 `keytool -list -v` 输出（发布证书已签发，为保证覆盖升级不可更换） |

> 该密钥仅用于调试与演示分发。**正式上线前请替换为单位自有密钥**，
> 否则后续升级包会因签名不一致无法覆盖安装。

> 注意：已发布 APK 的证书主题是历史生成的，属既有签名身份，
> 不在 App 界面或文档中展示任何单位名称；重新生成证书会导致无法覆盖安装。

## 生成正式密钥

```bash
keytool -genkeypair -v \
  -keystore keystore/powerline-release.jks \
  -alias powerline \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=输电线路工具箱, OU=运维, O=输电运维, C=CN"
```

## 查看 SHA1（高德 Key 绑定用）

```bash
keytool -list -v -keystore keystore/powerline-release.jks -alias powerline | grep SHA1
```

## 写入 local.properties

```properties
RELEASE_STORE_FILE=keystore/powerline-release.jks
RELEASE_STORE_PASSWORD=你的密钥库口令
RELEASE_KEY_ALIAS=powerline
RELEASE_KEY_PASSWORD=你的密钥口令
```

## 备份要求

- 密钥文件与口令需 **离线双备份**（U 盘 + 内网文件服务器）。
- 一旦丢失，已发布的 App 将无法升级，只能以新包名重新发布。
