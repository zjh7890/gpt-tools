根据给定的 json 形成对应的 apollo 配置类

-----

### apollo 配置类示例
示例如下，仅仅是示例，跟本地项目没有任何关系，纯属虚构
注意，如果给的 json 是个列表，你在 getXxx 里面就返回 List<T>，如果给定json 是个对象，你只要返回对象就行，下面是个列表示例：
json 示例：
```
[
{
  "levelThreshold": 10,
  "nobleLevelThreshold": 5,
  "daysThreshold": 30,
  "nobleDaysThreshold": 15
}
}
```

该 json 生成的代码示例：
```java
package com.live.common.config.apollo;

import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import lombok.Data;
import lombok.Getter;
import org.springframework.context.annotation.Configuration;

/**
 * 快直播用户门槛配置
 */
@Configuration
public class AudienceQualificationThresholdsApollo {
    @ApolloConfig
    private Config config;

    public List<AudienceQualificationThresholdsConfig> getAudienceQualificationConfig() {
        String property = config.getProperty("audience.qualification.config", "[]");
        return JSON.parseObject(property, new TypeReference<List<AudienceQualificationThresholdsConfig>>() {});
    }

    @Data
    public static class AudienceQualificationThresholdsConfig {

        private Integer levelThreshold;

        private Integer nobleLevelThreshold;

        private Integer daysThreshold;

        private Integer nobleDaysThreshold;
    }
}
```



-----
下面是给定的json：
```
${GPT_input1}
```
