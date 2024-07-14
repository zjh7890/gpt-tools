package com.github.zjh7890.gpttools

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.github.zjh7890.gpttools.services.MyProjectService
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
        GitDiffUtils.parseGitDiffOutput(diffText)
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
