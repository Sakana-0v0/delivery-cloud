# del-order 启动失败修复方案 - 后端工程师执行单

---

## 背景

del-order 服务启动失败，错误日志：

`
Caused by: org.springframework.context.annotation.ConflictingBeanDefinitionException:
Annotation-specified bean name 'rabbitMQConfig' for bean class 
[com.sakana.review.configs.RabbitMQConfig] conflicts with existing, 
non-compatible bean definition of same name and class [com.sakana.configs.RabbitMQConfig]
`

---

## 根因

del-order 依赖 del-product（pom.xml 中引入 <artifactId>del-product</artifactId>），导致 del-order 启动时 Spring 默认扫描 com.sakana 包，扫描到 del-product 的 review 包。两个服务都有同名类 RabbitMQConfig，Spring 默认以类名首字母小写作为 Bean 名称，都生成 abbitMQConfig，造成冲突。

| 服务 | 类全限定名 | 默认 Bean 名称 |
|------|-----------|---------------|
| del-order | com.sakana.configs.RabbitMQConfig | abbitMQConfig ⚠️ |
| del-product | com.sakana.review.configs.RabbitMQConfig | abbitMQConfig ⚠️ |

---

## 推荐修复方案：重命名 del-order 的 RabbitMQConfig 类

**优点**：
- 不影响其他包的扫描
- 保留所有功能
- 最稳妥，无副作用

**预计耗时**：5 分钟

---

## 执行步骤

### 步骤 1：重命名类文件

`
原文件：E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\configs\RabbitMQConfig.java
新文件：E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\configs\OrderRabbitMQConfig.java
`

### 步骤 2：修改类名

将类声明从：

`java
@Configuration
public class RabbitMQConfig {
`

改为：

`java
@Configuration
public class OrderRabbitMQConfig {
`

### 步骤 3：检查是否有引用

在 del-order 项目内搜索是否有引用 RabbitMQConfig 的地方：

`ash
grep -r "RabbitMQConfig" E:\Idea_project\delivery-cloud\del-order\src
`

如果有引用，更新为新类名 OrderRabbitMQConfig。

### 步骤 4：清理并重新打包

`ash
cd E:\Idea_project\delivery-cloud
mvn clean package -pl del-order -am
`

### 步骤 5：启动验证

`ash
java -jar E:\Idea_project\delivery-cloud\del-order\target\del-order-1.0-SNAPSHOT.jar
`

**预期输出**：

`
INFO --- [del-order] [main] com.sakana.OrderApplication : Started OrderApplication in X.XXX seconds
INFO --- [del-order] [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 10001
`

### 步骤 6：健康检查

`ash
curl http://localhost:10001/actuator/health
`

**预期输出**：

`json
{"status":"UP"}
`

---

## 备选方案（不推荐）

### 备选方案 1：在 OrderApplication 排除 review 包

修改 OrderApplication.java：

`java
@SpringBootApplication(
    scanBasePackages = "com.sakana",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.sakana\\.review\\..*"))
`

**缺点**：
- del-order 失去 review 相关类的访问能力
- 未来如需 review 功能，需重新启用

### 备选方案 2：显式指定 Bean 名称

修改两个 RabbitMQConfig 类：

`java
// del-order
@Configuration("orderRabbitMQConfig")
public class RabbitMQConfig { ... }

// del-product  
@Configuration("reviewRabbitMQConfig")
public class RabbitMQConfig { ... }
`

**缺点**：
- 需要修改两个文件
- 需要测试两个服务

---

## 验证清单

修复后请确认：

- del-order 启动成功（无 BeanDefinitionStoreException 错误）
- Tomcat 监听 10001 端口
- Nacos 注册成功
- /actuator/health 返回 {"status":"UP"}
- 创建订单接口正常（POST /api/v1/user/orders）
- 订单查询接口正常（GET /api/v1/user/orders）
- 其他服务不受影响（del-product、del-user 等）

---

## 同步检查

修复后，请同时检查其他依赖 del-product 的服务是否也存在类似问题：

`ash
grep -r "del-product" E:\Idea_project\delivery-cloud --include=pom.xml
`

预期可能受影响服务：
- del-order（已知）
- 其他服务（如有引入）需逐个验证

---

## 风险评估

| 风险项 | 等级 | 说明 |
|--------|------|------|
| 重命名影响范围 | 低 | del-order 内唯一同名类 |
| 引用更新遗漏 | 低 | grep 可全部找出 |
| 重新打包失败 | 低 | 仅修改类名 |
| 其他服务受影响 | 低 | 各自独立 |

---

*修复方案由 Codex 验收评审生成*
