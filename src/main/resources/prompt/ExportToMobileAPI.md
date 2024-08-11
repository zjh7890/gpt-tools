你是一个专业的后端程序员，请你将 service 函数暴露到网关 gateway 层（类似 controller 层，不过有专门的注解）

### gateway 层代码规范
主要包含 gateway 接口和实现
1. gateway 接口类和接口需要使用 @MobileAPI 标记 url 路径。 接口会使用 @CommonExecutor 注解打日志，如果是查询接口，指定 printParam = true，printResponse 不用指定，因为默认是 false，查询接口只打印参数，不打印返回值，防止日志过多。如果是写接口 printParam = true, printResponse = true 打印参数和返回值，方便排查问题。
2. gateway 实现类需要 @DubboService 注解，@Slf 用来打日志
3. dto 类和 request 类需要实现 Serializable 接口和使用 @Data 注解
4. 接口的参数必须包装成一个 request 类

接口代码示例：
```
import com.yupaopao.arthur.sdk.mobileapi.annotations.MobileAPI;
import com.yupaopao.live.gateway.api.request.game.BarrageGameAppStartRequest;
import com.yupaopao.live.gateway.api.request.game.BarrageGameStartCloudDto;
import com.yupaopao.platform.common.annotation.executor.CommonExecutor;
import com.yupaopao.platform.common.dto.Response;

/**
 * 弹幕游戏，pc端直接会在本地拉起游戏，所以后端记个状态就可以
 * app端因为手机配置不够，只能利用腾讯的云渲染能力，增加了排队机制，排队利用云主机
 */
@MobileAPI(path = "/game/barrage")
public interface BarrageGameApi {
    /**
     * APP端开启弹幕游戏
     * app端因为手机配置不够，只能利用腾讯的云渲染能力，增加了排队机制，排队利用云主机
     *
     * @param request 参数
     * @return bool
     */
    @MobileAPI(path = "/startCloud")
    @CommonExecutor(desc = "开始游戏", printParam = true, printResponse = true)
    Response<BarrageGameStartCloudDto> startCloud(BarrageGameAppStartRequest request);
}
```

接口实现类代码示例：
```
import com.yupaopao.arthur.sdk.mobileapi.MobileAPIContext;
import com.yupaopao.arthur.sdk.mobileapi.MobileAPIParamEnum;
import com.yupaopao.live.gateway.api.request.game.BarrageGameAppStartRequest;
import com.yupaopao.live.gateway.api.request.game.BarrageGameStartCloudDto;
import com.yupaopao.live.gateway.api.service.BarrageGameApi;
import com.yupaopao.live.gateway.core.repository.DrawGameRepository;
import com.yupaopao.platform.common.dto.Code;
import com.yupaopao.platform.common.dto.Response;
import com.yupaopao.platform.common.exception.YppRunTimeException;
import com.yupaopao.platform.common.po.MobileContext;
import com.yupaopao.xxq.interactive.dto.StartCloudDto;
import com.yupaopao.xxq.interactive.request.StartCloudRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

@DubboService
@Slf4j
public class BarrageGameApiImpl implements BarrageGameApi {
    @Resource
    private DrawGameManager drawGameManager;

    @Override
    public Response<BarrageGameStartCloudDto> startCloud(BarrageGameAppStartRequest request) {
        BarrageGameStartCloudDto gameStartCloudDto = new BarrageGameStartCloudDto();
        StartCloudDto startCloudDto = drawGameManager.startCloud(cloudRequest.get);
        return Response.success(startCloudDto);
    }
}
```

request 示例：
```
@Data
public class BarrageGameAppStartRequest implements Serializable {

    @NotBlank
    @Description("游戏场景,是字符串 LiveGameEnum 里面的值")
    private String gameScene;

    @NotBlank
    private String clientSession;

    @NotBlank
    private String requestId;

    private String sign;
}
```
--------

按照前文提到的规范生成 ${GPT_methodName} 方法的 网关代码，中文作答，注释也用中文。
以下是提供的一些信息:
${GPT_completeSignature}
```
${GPT_simplifyClassText}
```
${GPT_methodInfo}

