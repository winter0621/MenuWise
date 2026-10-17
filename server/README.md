# 国内 AI 热量查询服务

手机 → 你部署在国内的 HTTPS 后端 → 豆包、DeepSeek 或其他 OpenAI 兼容接口。
AI 查询可以免 Firebase 登录使用；原有 Firebase 登录、云端记录和旧拍照入口没有迁移。

## 开通与运行

1. 获取你使用平台的 API Key，并确认模型已开通。默认 DeepSeek `deepseek-flash`；通过 `MENUWISE_API_URL`、`MENUWISE_TEXT_MODEL`、`MENUWISE_VISION_MODEL` 可接入其他 OpenAI 兼容接口。DeepSeek 可填写 `https://api.deepseek.com`，文字与图片模型均使用 `deepseek-flash`。不支持图片的模型应将视觉模型留空。
2. 准备中国内地服务器和 HTTPS 域名。按云厂商要求完成域名备案等部署手续。
3. 安装 Python 3.10+，在本目录执行：

```sh
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

编辑 `.env`，填入真实 `MENUWISE_API_KEY`，并用以下命令生成独立的 `MENUWISE_ACCESS_TOKEN`：

```sh
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

环境文件不会自动加载。Linux 启动示例（`.env` 仅填写可信的环境变量赋值）：

```sh
set -a
. ./.env
set +a
uvicorn app:app --host 127.0.0.1 --port 8000 --workers 1
```

Windows PowerShell 可直接用 `$env:MENUWISE_API_KEY = '...'`、`$env:MENUWISE_ACCESS_TOKEN = '...'` 设置环境，再运行同一 uvicorn 命令。不要把模型密钥放入 Android 工程、APK 或提交到 Git。

4. 通过 HTTPS 反向代理到 `127.0.0.1:8000`。例如已有域名和证书的 Nginx：

```nginx
server {
    listen 443 ssl;
    server_name calories.example.cn;
    ssl_certificate /etc/ssl/calories/fullchain.pem;
    ssl_certificate_key /etc/ssl/calories/privkey.pem;
    client_max_body_size 3m;
    client_body_timeout 15s;
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_read_timeout 60s;
        proxy_set_header Host $host;
    }
}
```

5. 手机从欢迎页点击“AI 热量查询（免登录）”，或从首页进入“AI 热量查询”。在“服务设置”填写 `https://calories.example.cn` 和生成的访问码；地址不带 `/v1/calories`。手机与服务器均不需要 VPN，实际可达性需用国内移动网络验证。

`GET /health` 配置存在时返回 200；它不调用模型，不能证明密钥或模型可用。
首次真实查询用于验证服务、额度和模型权限。文字和照片会发送给该后端和配置的模型服务，照片不写入业务数据库，成功结果保存在手机本机。

## 请求与返回

`POST /v1/calories`，请求头 `Authorization: Bearer <服务访问码>` 和 `Content-Type: application/json`。

```json
{"text":"熟米饭200克，番茄炒蛋150克，少油","image_data":"data:image/jpeg;base64,..."}
```

`text` 与 `image_data` 至少填写一个；图片可省略。只接受不超过2MB的 JPEG/PNG 内嵌图片，不接受远程 URL。手机会缩小并压缩照片、处理方向，再由用户点击查询上传。

响应为 `items`、服务端计算的 `totals`、`assumptions` 和 `estimated: true`。每项包含 `name`、`grams`、`calories_kcal`、`protein_g`、`carbs_g`、`fat_g`，数值针对所描述的整份食物。AI结果是估算，没有食物营养数据库校验，无法替代称重和营养标签。

## 测试与使用范围

```sh
python -m unittest discover -v
```

测试覆盖请求校验、访问码、异常数值、非食物、汇总、限流、服务错误；模型调用使用 mock，不消耗额度，不代表真实识别准确率。

当前是个人/小范围试用部署：共用访问码，每分钟10次、滚动24小时默认200次、最多4个并发。限额为单进程内存状态，重启会重置，因此仅运行一个 worker，并在服务商控制台设置预算/额度。公开发布前应接入逐用户认证、持久化或 Redis 限流、账单监控，替换共用访问码。访问码可以从设备提取，不能作为公开产品的用户身份认证。

手机要求 HTTPS 且不跟随重定向，访问码和查询记录排除系统备份；它们仍存储在应用私有目录。查询记录最多保存50条、页面展示全部已保存记录（最多50条），可点开详情并加入计划，可在页面清空，不会自动计入摄入量。

## 待实际部署验证

- 国内 Wi-Fi/移动网络下文字查询、拍照、相册、无食物照片。
- 无访问码、错误访问码、断网、模型额度不足和超时提示。
- 查询期间旋转屏幕，以及相机返回后的照片方向。
- 输入已知重量的食物，与包装营养标签比较误差。
- 原有账号/同步模块仍依赖 Firebase；若要求整个应用国内直连，需迁移认证与数据库。

## 辅助编排每日热量计划

入口：首页“编排每日热量计划”，或免登录的 AI 热量查询页面中的同名按钮。

1. 选择计划日期，填写年龄、身高、体重、用于估算的性别、活动量和目标，以及饮食偏好和单独列出的忌口食材。
2. 点击“计算并保存热量分配”。默认早餐25%、午餐35%、晚餐30%、加餐10%，可改为合计100%的其他比例；0表示该餐不分配热量。
3. 各餐可以手动添加、调整、移除食物，标记已吃或填写实际食用克数；也可以从查询历史中加入整份查询结果。查询页面成功后还可以直接点击“将本次查询加入热量计划”。
4. 页面分别显示计划热量与已记录摄入。选择餐次提醒后会安排本机提醒，重启后恢复。计划按日期保存在本机，可用“已存计划”或日期选择器恢复。计划不会写入 Firebase。
5. 配置已有服务后可点击“AI 配餐建议”。身体资料保存在本机；AI 只收到餐次热量目标、饮食偏好和忌口。建议须确认后采用；食材检查基于文字匹配，不能保证过敏安全。
6. “计划工具”汇总未来七天已安排食材为采购清单，并支持勾选已购；本周回顾仅汇总用户标记的实际食用记录。热量计算、手动编排、采购清单和回顾可以离线使用。

计算采用 Mifflin–St Jeor 基础代谢估算：`10×体重kg + 6.25×身高cm − 5×年龄 + 性别常数`，男性常数5、女性−161；再乘活动系数1.2/1.375/1.55/1.725估计每日消耗。维持目标取估计消耗，温和减脂减少10%且最多300千卡，温和增重增加10%且最多200千卡，最终取整到10千卡。

本版面向一般成年人的参考规划。自定义目标限定在估计消耗的80%–120%附近，并设置女性1200、男性1500千卡的保守产品下限；这些数值不代表个体安全保证或医疗处方。低体重用户不生成减脂目标。身体资料不发送给模型，后端只收到每餐热量目标和饮食偏好。配餐和查询均为估算，严格过敏或医疗饮食需求仍需专业核对。

新增接口 `POST /v1/meal-plan`，与 `/v1/calories` 使用相同的访问码、HTTPS、并发和调用限额：

```json
{
  "preferences": "家常菜，少油，不吃香菜",
  "meals": [
    {"id":"breakfast","target_kcal":500},
    {"id":"lunch","target_kcal":700},
    {"id":"dinner","target_kcal":600},
    {"id":"snack","target_kcal":200}
  ]
}
```

0目标餐次不发送。`excluded_foods` 是逐项列出的忌口/过敏食材（支持逗号、顿号和分号分隔）。响应每餐 `items` 含 `name`、`ingredients`、`grams`、`calories_kcal`、`target_kcal` 和 `total_kcal`，以及全日合计、`notes`、`estimated: true`。后端检查已列食材中是否包含明确忌口，并拒绝缺少食材清单、重复餐次、异常数值及每餐偏离目标超过 `max(50千卡, 目标×15%)` 的建议；热量总数由服务器计算。

文字匹配无法识别所有食材别名、交叉污染或隐含成分；AI 不能保证过敏安全，严重过敏或医疗饮食需核对产品标签和专业建议。热量容差不代表营养准确率。

更新已有后端时需要同步本目录并重启服务，旧服务没有 `/v1/meal-plan` 接口。模型真实调用和份量准确性仍需配置有效密钥后验证；自动化测试使用 mock，不消耗模型额度。


## 本机页面与记录流程更新

- 欢迎页可直接进入离线饮食计划；AI 查询返回时会保留欢迎页。
- 首页按所选日期读取本机计划的目标与实际记录，返回首页时刷新。原有云端拍照记录与本机计划分开显示。
- 已有计划默认收起资料表单；支持复制到其他日期，复制不携带摄入记录与提醒，覆盖已有日期前需要确认。
- 实际份量可以超过计划份量，调整计划保留已有实际记录。餐次提醒可打开对应日期及餐次。
- 采购清单从所选计划日期起汇总七天，支持已购勾选、重置及通过系统分享面板分享未购项目。
- 周回顾只统计截至所选日期已经记录的摄入，未记录日期不按零处理。

Android 验证：`gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline`。
新增回归测试覆盖超出计划的实际份量、复制时清除摄入与提醒、无效日期，以及周回顾排除未记录和未来日期。


## 图片识别更新

默认视觉模型为 `deepseek-flash`（可通过 `MENUWISE_VISION_MODEL` 指定已开通的模型或接入点 ID）；已部署服务需更新环境变量并重启才会采用新配置。图片请求使用高细节模式，提示词按整餐逐项分析重量、生熟状态和用油，避免重复计数。

每项可返回 `calories_min_kcal` / `calories_max_kcal`，必须覆盖 `calories_kcal`。仅当全部食物都有区间时才汇总整餐区间；区间是模型假设，不是统计置信区间。额外校验份量、营养素总重与热量密度，并由程序计算总数。省略区间的旧响应仍兼容。

Android 新增个人直连方式，默认仅显示 API Key 输入框，不显示服务商选择；首次默认 DeepSeek，其他兼容接口在可选高级设置中填写地址与模型，不依赖此后端。密钥由 Android Keystore 加密，调用额度属于该 Key 的账户。现有网关模式和共用访问码部署方式仍可使用。
