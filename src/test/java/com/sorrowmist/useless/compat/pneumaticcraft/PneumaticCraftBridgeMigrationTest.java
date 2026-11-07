package com.sorrowmist.useless.compat.pneumaticcraft;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PneumaticCraftBridgeMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void extractionIsSimulatableAndCannotCreateAVacuum() throws Exception {
        var air = new AtomicInteger(4000);
        var endpoint = endpoint(air);
        assertEquals(4000, endpoint.extract(Long.MAX_VALUE, true));
        assertEquals(4000, air.get());
        assertEquals(4000, endpoint.extract(Long.MAX_VALUE, false));
        assertEquals(0, air.get());
        assertEquals(0, endpoint.extract(1, false));
        assertEquals(-1000, endpoint.setAirTo(Long.MIN_VALUE, false));
        assertEquals(0, endpoint.extract(1, false));
        assertEquals(-1.0, endpoint.pressure());
        assertEquals(5000, endpoint.capacity());
    }

    @Test void longTargetsReportOnlyTheNativeIntDeltaThatWasActuallyApplied() throws Exception {
        var air = new AtomicInteger(-1000);
        var endpoint = endpoint(air);
        assertEquals(Integer.MAX_VALUE, endpoint.setAirTo(Long.MAX_VALUE, true));
        assertEquals(-1000, air.get());
        assertEquals(Integer.MAX_VALUE, endpoint.setAirTo(Long.MAX_VALUE, false));
        assertEquals(Integer.MAX_VALUE - 1000, air.get());
        assertEquals(1000, endpoint.receive(Long.MAX_VALUE, false));
        assertEquals(Integer.MAX_VALUE, air.get());
        assertEquals(0, endpoint.receive(Long.MAX_VALUE, false));
        assertEquals(Integer.MIN_VALUE, endpoint.setAirTo(Long.MIN_VALUE, false));
        assertEquals(-1, air.get());
        assertEquals(-999, endpoint.setAirTo(Long.MIN_VALUE, false));
        assertEquals(-1000, air.get());
    }

    @Test void thermostatKeepsTheSameHeatEndpointAndChangesResistanceWithoutReconnecting() throws Exception {
        var thermostat = mock(ThermostatBlockEntity.class);
        doCallRealMethod().when(thermostat).putHeatAdapter(anyString(), any());
        when(thermostat.getHeatAdapter(anyString())).thenCallRealMethod();
        when(thermostat.isEnabled()).thenReturn(false);
        when(thermostat.getTemperature()).thenReturn(1234L);
        var method = ThermostatHeatCompat.class.getDeclaredMethod("adapter", ThermostatBlockEntity.class);
        method.setAccessible(true);
        var logic = (IHeatExchangerLogic) method.invoke(null, thermostat);
        assertSame(logic, method.invoke(null, thermostat));
        assertEquals(300.0, logic.getTemperature());
        assertEquals(10_000.0, logic.getThermalResistance());
        when(thermostat.isEnabled()).thenReturn(true);
        assertEquals(1234.0, logic.getTemperature());
        assertEquals(1.0, logic.getThermalResistance());
        logic.addHeat(100_000); logic.setTemperature(1);
        assertEquals(1234.0, logic.getTemperature());
    }

    private static LongPressureHandler endpoint(AtomicInteger air) throws Exception {
        var handler = mock(IAirHandlerMachine.class);
        when(handler.getAir()).thenAnswer(invocation -> air.get());
        when(handler.getVolume()).thenReturn(1000);
        when(handler.getDangerPressure()).thenReturn(5.0F);
        doAnswer(invocation -> { air.set(Math.max(-1000, air.get() + invocation.<Integer>getArgument(0))); return null; })
                .when(handler).addAir(anyInt());
        var ctor = Class.forName(PneumaticCraftPressureCompat.class.getName() + "$AirEndpoint")
                .getDeclaredConstructor(IAirHandlerMachine.class);
        ctor.setAccessible(true);
        return (LongPressureHandler) ctor.newInstance(handler);
    }
}
