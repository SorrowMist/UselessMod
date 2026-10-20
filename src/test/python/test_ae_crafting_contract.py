"""Regression guards for lazy AE2/AdvancedAE mixins against pinned native jars.

Run after building the main jar. Does not start Minecraft or edit the pack.
"""
from pathlib import Path
import io
import json
import re
import struct
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
INPUTS = ROOT / 'build/contract-inputs.json'


def class_contract(data):
    stream = io.BytesIO(data)
    def read(fmt):
        return struct.unpack('>' + fmt, stream.read(struct.calcsize('>' + fmt)))[0]
    assert read('I') == 0xCAFEBABE
    read('H'); read('H')
    pool = [None] * read('H')
    index = 1
    sizes = {3:4, 4:4, 5:8, 6:8, 7:2, 8:2, 9:4, 10:4, 11:4,
             12:4, 15:3, 16:2, 17:4, 18:4, 19:2, 20:2}
    while index < len(pool):
        tag = read('B')
        if tag == 1:
            pool[index] = stream.read(read('H')).decode('utf-8', errors='replace')
        else:
            stream.read(sizes[tag])
        index += 2 if tag in (5, 6) else 1
    read('H'); read('H'); read('H')
    stream.read(2 * read('H'))
    def members():
        result = {}
        for _ in range(read('H')):
            read('H'); name = pool[read('H')]; desc = pool[read('H')]
            result[name] = result.get(name, []) + [desc]
            for _ in range(read('H')):
                read('H'); stream.read(read('I'))
        return result
    members()
    return members(), [value for value in pool if isinstance(value, str)]


def load(jar, name):
    with zipfile.ZipFile(jar) as archive:
        return class_contract(archive.read(name.replace('.', '/') + '.class'))


class AeCraftingMixinContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        version = re.search(r'(?m)^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(encoding='utf-8'))[1].strip()
        cls.jar = ROOT / 'build/libs' / ('useless_mod-' + version + '.jar')
        cls.native = {key: Path(value) for key, value in json.loads(INPUTS.read_text(encoding='utf-8')).items()}

    def check_cpu(self, family, target):
        handlers, strings = load(self.jar, 'com.sorrowmist.useless.mixin.' + family + '.CraftingCpuLogicMixin')
        methods, _ = load(self.native[family], target)
        for hook, native in [('uselessMod$writeDynamicState', 'writeToNBT'),
                             ('uselessMod$readDynamicState', 'readFromNBT')]:
            with self.subTest(family=family, hook=hook):
                self.assertIn('(Lnet/minecraft/nbt/CompoundTag;)V', methods[native])
                descriptor = handlers[hook][0].replace(
                    'Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;', '')
                self.assertIn(descriptor, methods[native])
                self.assertIn(native + '(Lnet/minecraft/nbt/CompoundTag;)V', strings)

    def test_ae2_cpu_native_nbt(self):
        self.check_cpu('ae2', 'appeng.crafting.execution.CraftingCpuLogic')

    def test_advancedae_cpu_native_nbt(self):
        self.check_cpu('advancedae', 'net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic')

    def check_restore(self, family, target):
        _, strings = load(self.jar, 'com.sorrowmist.useless.mixin.' + family + '.ExecutingCraftingJobMixin')
        native, _ = load(self.native[family], target)
        descriptors = [s[len('<init>'):] for s in strings if s.startswith('<init>(')]
        self.assertEqual(len(descriptors), 1)
        self.assertIn(descriptors[0], native['<init>'])

    def test_ae2_restore_constructor(self):
        self.check_restore('ae2', 'appeng.crafting.execution.ExecutingCraftingJob')

    def test_advancedae_restore_constructor(self):
        self.check_restore('advancedae', 'net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob')

    def test_no_mixin_synthetic_args_dependency(self):
        _, strings = load(self.jar, 'com.sorrowmist.useless.mixin.ae2.CraftingTreeProcessMixin')
        self.assertFalse(any('ModifyArgs' in s or 'injection/invoke/arg/Args' in s for s in strings))
        self.assertIn('Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;', strings)
        self.assertIn('appeng/crafting/CraftingTreeNode', strings)

    def test_registry_free_state_storage(self):
        methods, _ = load(self.jar, 'com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager')
        self.assertIn('(Ljava/lang/Object;)Lnet/minecraft/nbt/CompoundTag;', methods['writeToTag'])
        self.assertIn('(Ljava/lang/Object;Ljava/util/UUID;Lnet/minecraft/nbt/CompoundTag;)V', methods['readFromTag'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
