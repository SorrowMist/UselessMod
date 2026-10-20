package com.sorrowmist.useless.compat.forge1201;

import java.lang.reflect.InvocationTargetException;
import net.minecraftforge.fml.ModList;

/** Optional source boundaries: no class linkage when the corresponding mod is absent. */
public final class OptionalIntegration {
    private OptionalIntegration() {}

    public static boolean hasClass(String className) {
        return OptionalIntegration.class.getClassLoader()
                .getResource(className.replace('.', '/') + ".class") != null;
    }

    public static <T> T call(String modId,String className,String method,Class<T> resultType,T absent,
            Class<?>[] signature,Object... arguments) {
        if (!ModList.get().isLoaded(modId)) return absent;
        try {
            Object result=Class.forName(className).getMethod(method,signature).invoke(null,arguments);
            return resultType==Void.class ? null : resultType.cast(result);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Integration "+modId+" failed in "+className+"#"+method,e.getCause());
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Installed integration "+modId+" is unavailable in this build: "+className,e);
        }
    }
}
