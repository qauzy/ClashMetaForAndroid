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
- proxyProvider与内核处理不一致 → 节点空 (2026-07-02)
  - 根因：CFA的fetch()和内核HTTPVehicle.Read()下载provider时处理方式不一致
  - HTTPVehicle.Read()处理X-UUID解密、Cloudflare回退、proxy支持，CFA的fetch()都不做
  - 修复方式：fetch.go中用resource.NewHTTPVehicle+vehicle.Read()下载provider(和内核Update()一样)，再AES加密写盘
  - 不修改内核fetcher.go，外壳和内核对provider文件处理方式统一
