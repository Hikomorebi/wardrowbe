package site.hikomorebi.wardrobe.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun seasonsAreReadableLabelsWithoutFourSeasonOption() {
        compose.setContent { WardrobeTheme { SeasonBadges(listOf("winter", "spring")) } }
        compose.onNodeWithText("春季").assertIsDisplayed().assert(hasClickAction().not())
        compose.onNodeWithText("冬季").assertIsDisplayed()
        compose.onNodeWithText("四季").assertDoesNotExist()
        compose.onNodeWithText("夏季").assertDoesNotExist()
    }

    @Test
    fun categorySelectionCanBeCleared() {
        var selected: String? = "shirt"
        compose.setContent {
            WardrobeTheme {
                ChoiceField("类别", "shirt", listOf("shirt" to "上衣"), onChange = { selected = it })
            }
        }
        compose.onNodeWithText("上衣").performClick()
        compose.onNodeWithText("未设置").performClick()
        assertNull(selected)
    }

    @Test
    fun destructiveActionIsDisabledWhileSaving() {
        compose.setContent {
            WardrobeTheme {
                ConfirmDialog("删除衣物？", "衣物图片将被删除", true, onDismiss = {}, onConfirm = {})
            }
        }
        compose.onNodeWithText("删除").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
    }
}
