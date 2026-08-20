package com.sorrowmist.useless.content.items;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndlessBeafItemTest {
    @Test
    void attackDamageModifierUsesRecipeCountAndKeepsNetheriteBonus() {
        AttributeModifier modifier = EndlessBeafItem.createAttackDamageModifier(123);

        assertEquals(Item.BASE_ATTACK_DAMAGE_ID, modifier.id());
        assertEquals(123 + Tiers.NETHERITE.getAttackDamageBonus(), modifier.amount());
        assertEquals(AttributeModifier.Operation.ADD_VALUE, modifier.operation());
    }

    @Test
    void attributeEventReplacesDamageWithoutChangingAttackSpeed() {
        ItemAttributeModifiers defaults = DiggerItem.createAttributes(Tiers.NETHERITE, 0, 2.0F);
        ItemAttributeModifierEvent event = new ItemAttributeModifierEvent(ItemStack.EMPTY, defaults);

        event.replaceModifier(
                Attributes.ATTACK_DAMAGE,
                EndlessBeafItem.createAttackDamageModifier(123),
                EquipmentSlotGroup.MAINHAND);

        ItemAttributeModifiers result = event.build();
        ItemAttributeModifiers.Entry damage = result.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
                .findFirst()
                .orElseThrow();
        ItemAttributeModifiers.Entry speed = result.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED))
                .findFirst()
                .orElseThrow();

        assertEquals(123 + Tiers.NETHERITE.getAttackDamageBonus(), damage.modifier().amount());
        assertEquals(2.0D, speed.modifier().amount());
    }
}
