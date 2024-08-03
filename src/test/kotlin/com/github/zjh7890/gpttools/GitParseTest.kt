package com.github.zjh7890.gpttools

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.github.zjh7890.gpttools.services.MyProjectService
import com.github.zjh7890.gpttools.utils.DrawioToMermaidConverter
import com.github.zjh7890.gpttools.utils.GitDiffUtils

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class GitParseTest : BasePlatformTestCase() {
    val diffText = """
diff --git a/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/GamePanelDTO.java b/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/GamePanelDTO.java
index 213140f5..b6b326db 100644
--- a/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/GamePanelDTO.java
+++ b/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/GamePanelDTO.java
@@ -14,4 +15,6 @@ public class GamePanelDTO implements Serializable {
     private Boolean available;

     private List<LinkGameDTO> gameList;
-
-    private List<LinkGameDetailDTO> sudGameList;
- }
diff --git a/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/LinkGameDetailDTO.java b/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/LinkGameDetailDTO.java
new file mode 100644
index 00000000..753292f2
--- /dev/null
+++ b/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/LinkGameDetailDTO.java
@@ -0,0 +1,26 @@
+package com.yupaopao.live.link.api.dto;
+
+import lombok.Data;
+
+import java.io.Serializable;
+
+/**
+ * 游戏详细信息
+ *
+ * @author: liuchuan
+ */
+@Data
+public class LinkGameDetailDTO implements Serializable {
+
+    /**
+     * 游戏信息
+     */
+    private LinkGameInfoDTO gameInfo;
+
+    /**
+     * 游戏规则
+     */
+    private LinkGameRuleDTO gameRule;
+
+
+}
diff --git a/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/LinkGameInfoDTO.java b/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/LinkGameInfoDTO.java
new file mode 100644
index 00000000..a8730db3
--- /dev/null
+++ b/live-link-api/src/main/java/com/yupaopao/live/link/api/dto/LinkGameInfoDTO.java
@@ -0,0 +1,82 @@
+package com.yupaopao.live.link.api.dto;
+
+import lombok.Data;
+
+import java.io.Serializable;
+import java.util.List;
+
+/**
+ * @author: liuchuan
+ */
+@Data
+public class LinkGameInfoDTO implements Serializable {
+
+    private Long id;
+
+    private String scene;
+
+    private String name;
+
+    private String icon;
+
+    private String pcIcon;
+
+    private String pcHoverIcon;
+
+    private String svgaUrl;
+
+    private String pcSvgaUrl;
+
+    private Integer svgaSort;
+
+    private String scheme;
+
+    private String uatScheme;
+
+    private List<Long> topCategory;
+
+    private List<Integer> liveType;
+
+    private Integer sequence;
+
+    private Integer state;
+
+    private String appVersion;
+
+    private String pcVersion;
+
+    private String bxVersion;
+
+    private String yuerAppVersion;
+
+    private String yuerPcVersion;
+
+    private String mvpVersion;
+
+    private Boolean isGray;
+
+    private List<Integer> userType;
+
+    private String ext;
+
+    private Boolean isSupportRound;
+
+    private String tagImg;
+
+    /**
+     * 分类ID
+     * 展示列表分类
+     */
+    @Deprecated
+    private Integer catId;
+
+    private Integer displayId;
+
+    private String effectiveTime;
+
+    private String gameSupplier;
+
+    private LinkSudGameConfigDTO sudGameConfig;
+
+
+}
"""

    fun testMain() {
//        GitDiffUtils.parseGitDiffOutput(diffText)

        val encodedData = "%3CmxGraphModel%3E%3Croot%3E%3CmxCell%20id%3D%22UsZzD9oq4vvyecdlqx8fj%22%2F%3E%3CmxCell%20id%3D%22atBsG_KimkLpMsiTyBbJu%22%20parent%3D%22UsZzD9oq4vvyecdlqx8fj%22%2F%3E%3CmxCell%20id%3D%22l6OqBO1_-W6Wm21BStyxJ%22%20value%3D%22%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3B%22%20edge%3D%221%22%20source%3D%22NDrPDHHnDkOc6gv5YX8fw%22%20target%3D%22lbZ5rW1O30QlEBeSSEc0m%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22390%22%20y%3D%22780%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22NDrPDHHnDkOc6gv5YX8fw%22%20value%3D%22%E6%89%93%E8%B5%8F%E6%88%90%E5%8A%9F%20%2F%20%E5%8F%91%E9%80%81%20sud%22%20style%3D%22rounded%3D1%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3BfontFamily%3DHelvetica%3BfontSize%3D12%3BfontColor%3D%23000000%3Balign%3Dcenter%3BstrokeColor%3D%23000000%3BfillColor%3D%23ffffff%3BarcSize%3D50%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22330%22%20y%3D%22720%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22ZrkvdKQqsiySAfSAMsLK1%22%20style%3D%22rounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3BexitX%3D0.5%3BexitY%3D1%3BexitDx%3D0%3BexitDy%3D0%3B%22%20edge%3D%221%22%20source%3D%22o7MmdJYWeuKsIsdbF9qi9%22%20target%3D%22yq2eTx_IJlQJ6pq5SL02d%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22232.5%22%20y%3D%22470%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22NMdJ86H5wRG6p7x66feVj%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3B%22%20edge%3D%221%22%20source%3D%22o7MmdJYWeuKsIsdbF9qi9%22%20target%3D%22NDrPDHHnDkOc6gv5YX8fw%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22320%22%20y%3D%22470%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22o7MmdJYWeuKsIsdbF9qi9%22%20value%3D%22%E5%B7%B2%E5%8A%A0%E5%85%A5%E6%88%98%E9%98%9F%EF%BC%8C%E5%88%A4%E6%96%AD%E6%98%AF%E5%90%A6%E5%BD%93%E5%89%8D%E6%89%80%E5%9C%A8%E6%88%98%E9%98%9F%22%20style%3D%22rounded%3D0%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22260%22%20y%3D%22410%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22Js9cNkNvKnkZozVU2AA5Z%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3B%22%20edge%3D%221%22%20source%3D%22xWnQVsDbhL5JVBtuNThJG%22%20target%3D%22NDrPDHHnDkOc6gv5YX8fw%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22390%22%20y%3D%22650%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22xWnQVsDbhL5JVBtuNThJG%22%20value%3D%22%E6%9C%AA%E5%8A%A0%E5%85%A5%E6%88%98%E9%98%9F%EF%BC%8C%E5%8A%A0%E6%88%98%E9%98%9F%22%20style%3D%22rounded%3D0%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22490%22%20y%3D%22620%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22yq2eTx_IJlQJ6pq5SL02d%22%20value%3D%22%E6%89%93%E8%B5%8F%E5%A4%B1%E8%B4%A5%20%2F%20%E4%B8%8D%E5%8F%91%E9%80%81%20sud%22%20style%3D%22rounded%3D1%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3BarcSize%3D50%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22120%22%20y%3D%22520%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22J1Kia3tLWxIkQ368VlKt0%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3B%22%20edge%3D%221%22%20source%3D%22FKNYUdmjkTat0h7qTsrW5%22%20target%3D%22P-BziU47kYEgUcSD16_eP%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22480%22%20y%3D%22270%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%220nc0xt79gP_rmn8RmzMHP%22%20value%3D%22Y%22%20style%3D%22edgeLabel%3Bhtml%3D1%3Balign%3Dcenter%3BverticalAlign%3Dmiddle%3Bresizable%3D0%3Bpoints%3D%5B%5D%3B%22%20vertex%3D%221%22%20connectable%3D%220%22%20parent%3D%22J1Kia3tLWxIkQ368VlKt0%22%3E%3CmxGeometry%20x%3D%220.0222%22%20y%3D%22-2%22%20relative%3D%221%22%20as%3D%22geometry%22%3E%3CmxPoint%20as%3D%22offset%22%2F%3E%3C%2FmxGeometry%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22TQ-Fu8vw0LNUghqODog2a%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3BentryX%3D0.5%3BentryY%3D0%3BentryDx%3D0%3BentryDy%3D0%3B%22%20edge%3D%221%22%20source%3D%22FKNYUdmjkTat0h7qTsrW5%22%20target%3D%22o7MmdJYWeuKsIsdbF9qi9%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22320%22%20y%3D%22300%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22Stcvfmi5G-u9Ma7t7kiL3%22%20value%3D%22N%22%20style%3D%22edgeLabel%3Bhtml%3D1%3Balign%3Dcenter%3BverticalAlign%3Dmiddle%3Bresizable%3D0%3Bpoints%3D%5B%5D%3B%22%20vertex%3D%221%22%20connectable%3D%220%22%20parent%3D%22TQ-Fu8vw0LNUghqODog2a%22%3E%3CmxGeometry%20x%3D%220.0762%22%20y%3D%22-3%22%20relative%3D%221%22%20as%3D%22geometry%22%3E%3CmxPoint%20as%3D%22offset%22%2F%3E%3C%2FmxGeometry%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22FKNYUdmjkTat0h7qTsrW5%22%20value%3D%22%E6%9F%A5%E8%AF%A2%E6%88%98%E9%98%9F%E7%BC%93%E5%AD%98%20Redis%22%20style%3D%22rhombus%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3BfontFamily%3DHelvetica%3BfontSize%3D12%3BfontColor%3D%23000000%3Balign%3Dcenter%3BstrokeColor%3D%23000000%3BfillColor%3D%23ffffff%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22360%22%20y%3D%22240%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22-EIUlMBQRPOT8pDsLw7WW%22%20value%3D%22%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3B%22%20edge%3D%221%22%20source%3D%22IJSDoLOITt0v0zj9qAHZx%22%20target%3D%22FOrtNBwqdM1CgAQdNTnFw%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22550%22%20y%3D%22490%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22IJSDoLOITt0v0zj9qAHZx%22%20value%3D%22setNx%20%E5%B9%B6%E5%8F%91%E6%8E%A7%E5%88%B6%22%20style%3D%22rounded%3D0%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22490%22%20y%3D%22430%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22I1nn_Mqdd1KNcETycD9BZ%22%20value%3D%22%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3B%22%20edge%3D%221%22%20source%3D%22FOrtNBwqdM1CgAQdNTnFw%22%20target%3D%22xWnQVsDbhL5JVBtuNThJG%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22550%22%20y%3D%22580%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22BwEC5LmrIMRNx5CTvh5-w%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3BentryX%3D1%3BentryY%3D0.5%3BentryDx%3D0%3BentryDy%3D0%3B%22%20edge%3D%221%22%20source%3D%22FOrtNBwqdM1CgAQdNTnFw%22%20target%3D%22o7MmdJYWeuKsIsdbF9qi9%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22380%22%20y%3D%22440%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22FOrtNBwqdM1CgAQdNTnFw%22%20value%3D%22%E6%9F%A5%E8%AF%A2%E6%88%98%E9%98%9F%E7%BC%93%E5%AD%98%20Redis%22%20style%3D%22rounded%3D0%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22490%22%20y%3D%22520%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%228086rm964sfLaZ9_tHFW4%22%20value%3D%22%22%20style%3D%22edgeStyle%3DorthogonalEdgeStyle%3Brounded%3D0%3BorthogonalLoop%3D1%3BjettySize%3Dauto%3Bhtml%3D1%3B%22%20edge%3D%221%22%20source%3D%22P-BziU47kYEgUcSD16_eP%22%20target%3D%22IJSDoLOITt0v0zj9qAHZx%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22550%22%20y%3D%22410%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22P-BziU47kYEgUcSD16_eP%22%20value%3D%22%E6%89%80%E5%9C%A8%E7%9B%B4%E6%92%AD%E9%97%B4%E6%98%AF%E5%90%A6%E5%BC%B9%E5%B9%95%E6%B8%B8%E6%88%8F%E4%B8%AD%22%20style%3D%22rounded%3D0%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3BfillColor%3D%23FFFFFF%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22490%22%20y%3D%22350%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3CmxCell%20id%3D%22lbZ5rW1O30QlEBeSSEc0m%22%20value%3D%22%E8%AE%B0%E5%BD%95%20record%EF%BC%8C%E5%8A%A0%E5%85%A5%E6%88%98%E9%98%9F%22%20style%3D%22rounded%3D1%3BwhiteSpace%3Dwrap%3Bhtml%3D1%3BarcSize%3D50%3B%22%20vertex%3D%221%22%20parent%3D%22atBsG_KimkLpMsiTyBbJu%22%3E%3CmxGeometry%20x%3D%22330%22%20y%3D%22820%22%20width%3D%22120%22%20height%3D%2260%22%20as%3D%22geometry%22%2F%3E%3C%2FmxCell%3E%3C%2Froot%3E%3C%2FmxGraphModel%3E"


        val mermaidContent = DrawioToMermaidConverter.convert(encodedData)
        println(mermaidContent)
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
