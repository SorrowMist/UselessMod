"""Guard the Forge 1.20.1 world-outline transform, including the same bug's sibling renderers."""
from pathlib import Path
import re
import unittest
from test_ae_crafting_contract import load

ROOT = Path(__file__).resolve().parents[3]
RENDER = ROOT / 'src/main/java/com/sorrowmist/useless/client/render'
RENDERERS = ['AeLinkHighlightRenderer', 'StaffLinkHighlightRenderer',
             'ChainMiningHighlightRenderer', 'AlloyFurnaceErrorHighlightRenderer',
             'ConstructionWandPreviewRenderer']


class OutlineContractTest(unittest.TestCase):
    def test_current_built_jar_uses_world_stage_for_all_five_renderers(self):
        version = re.search(r'(?m)^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(encoding='utf-8'))[1].strip()
        jar = ROOT / 'build/libs' / ('useless_mod-' + version + '.jar')
        for name in RENDERERS:
            with self.subTest(renderer=name):
                _, strings = load(jar, 'com.sorrowmist.useless.client.render.' + name)
                self.assertIn('AFTER_PARTICLES', strings)
                self.assertNotIn('AFTER_LEVEL', strings)
                self.assertNotIn('org/joml/Quaternionf', strings)

    def test_world_view_stage_without_duplicate_rotation_or_projection(self):
        for name in RENDERERS:
            with self.subTest(renderer=name):
                source = (RENDER / (name + '.java')).read_text(encoding='utf-8')
                source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
                self.assertIn('RenderLevelStageEvent.Stage.AFTER_PARTICLES', source)
                self.assertNotIn('RenderLevelStageEvent.Stage.AFTER_LEVEL', source)
                self.assertNotIn('Quaternionf', source)
                self.assertNotIn('getProjectionMatrix', source)
                self.assertNotIn('.mulPose(', source)
                self.assertIn('move(-cameraPos.x, -cameraPos.y, -cameraPos.z)', source)

    def test_ae_and_logistics_do_not_draw_late_responses_when_tool_is_disabled(self):
        for name in RENDERERS[:2]:
            with self.subTest(renderer=name):
                source = (RENDER / (name + '.java')).read_text(encoding='utf-8')
                render = source[source.index('public static void onRenderStage'):]
                self.assertIn('!shouldRequest(minecraft)', render.split('Camera camera')[0])


if __name__ == '__main__':
    unittest.main()
