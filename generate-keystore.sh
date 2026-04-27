#!/bin/bash
# generate-keystore.sh - 生成 Android 签名密钥
# 用法: ./generate-keystore.sh

KEYSTORE_FILE="traktneosync.keystore"
KEY_ALIAS="traktneosync"
KEYSTORE_PASSWORD=$(openssl rand -base64 12)
KEY_PASSWORD=$(openssl rand -base64 12)

echo "=========================================="
echo "生成 Android 签名密钥"
echo "=========================================="

# 检查是否安装了 keytool
if ! command -v keytool &> /dev/null; then
    echo "错误: 未找到 keytool。请确保已安装 JDK 并添加到 PATH"
    exit 1
fi

# 生成密钥
keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=TraktNeoSync, OU=App, O=Developer, L=Unknown, ST=Unknown, C=CN" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD"

echo ""
echo "=========================================="
echo "签名密钥已生成!"
echo "=========================================="
echo ""
echo "文件: $KEYSTORE_FILE"
echo "别名: $KEY_ALIAS"
echo "密钥库密码: $KEYSTORE_PASSWORD"
echo "密钥密码: $KEY_PASSWORD"
echo ""
echo "=========================================="
echo "GitHub Actions Secrets 配置"
echo "=========================================="
echo ""
echo "请前往 GitHub 仓库 Settings -> Secrets and variables -> Actions"
echo "添加以下 Secrets:"
echo ""
echo "SIGNING_KEY_BASE64:"
base64 -w 0 "$KEYSTORE_FILE"
echo ""
echo ""
echo "SIGNING_KEY_ALIAS: $KEY_ALIAS"
echo "SIGNING_KEY_PASSWORD: $KEY_PASSWORD"
echo "SIGNING_STORE_PASSWORD: $KEYSTORE_PASSWORD"
echo ""
echo "=========================================="
echo "本地使用 (可选)"
echo "=========================================="
echo ""
echo "在 local.properties 中添加:"
echo "SIGNING_KEY_ALIAS=$KEY_ALIAS"
echo "SIGNING_KEY_PASSWORD=$KEY_PASSWORD"
echo "SIGNING_STORE_PASSWORD=$KEYSTORE_PASSWORD"
echo ""
echo "将 $KEYSTORE_FILE 放在 app/ 目录下"
