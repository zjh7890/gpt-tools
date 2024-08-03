根据给定的 json 形成对应的 apollo 配置类

-----

### apollo 配置类示例
示例如下，仅仅是示例，跟本地项目没有任何关系，纯属虚构:
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
下面是给定的信息：
```json
[
  {
    "danName": "黑铁",
    "winPoints": 0,
    "danLevelIcon": "https://p6.hellobixin.com/bx-user/b955c52517f849c3b4935ab4cf7ae79e.png"
  },
  {
    "danName": "青铜",
    "winPoints": 51,
    "danLevelIcon": "https://p6.hellobixin.com/bx-user/edd86a71545e41ac9ce623f055393189.png"
  },
  {
    "danName": "白银",
    "winPoints": 201,
    "danLevelIcon": "https://p6.hellobixin.com/bx-user/d6ca984771cd459e9464ac912d834a2e.png"
  },
  {
    "danName": "黄金",
    "winPoints": 501,
    "danLevelIcon": "https://p6.hellobixin.com/bx-user/d9a848cbfda34da190053375cef7bd5e.png"
  },
  {
    "danName": "翡翠",
    "winPoints": 1001,
    "danLevelIcon": "https://p6.hellobixin.com/bx-user/483b91772d2f4c548aa11f8497a63b5c.png"
  },
  {
    "danName": "大师",
    "winPoints": 2001,
    "danLevelIcon": "https://p6.hellobixin.com/bx-user/7c196d7b0a1c4346afdda2093fffabcc.png"
  },
  {
    "danName": "王者",
    "winPoints": 3001,
    "danLevelIcon": "https://p6.hellobixin.com/bx-user/b725a989fd7140aa8d3f6465631940ba.png"
  }
]
```
