# ClashMetaForAndroid 项目记忆

## 项目概况
- Android 上的 Clash Meta (mihomo) 客户端
- Kotlin (app/service) + Go (core) 混合架构
- mihomo 内核作为本地依赖: `core/src/foss/golang/clash/`

## 关键架构
- **数据流**: URL导入 → FetchAndValid(下载) → ProfileProcessor(copyRecursively) → Load(内核加载)
- **目录结构**: processingDir → importedDir/uuid，provider文件在 providers/hash 子目录
- **内核加密**: mihomo Fetcher.Initial() 对本地缓存使用 AES-256-CFB 加密(GenerateAESKey按月变化)
- **CFA patch**: patchProviders 把 provider path 改为 profileDir/providers/hash (每profile独立)

## 已修复问题
- proxyProvider未加密文件与内核Decrypt不一致 → 节点空 (2026-07-02)
  - 修复方式：fetch.go下载provider后用utils.Encrypt加密保存，与内核loadBuf加密方式一致
  - 不修改内核fetcher.go，外壳和内核对provider文件处理方式统一即可
