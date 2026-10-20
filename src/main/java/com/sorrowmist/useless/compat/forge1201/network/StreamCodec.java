package com.sorrowmist.useless.compat.forge1201.network;

import java.util.function.*;
/** A buffer codec only; it deliberately does not add classes to Minecraft's packages. */
public interface StreamCodec<B,V> {
    V decode(B buffer);
    void encode(B buffer, V value);
    static <B,V> StreamCodec<B,V> of(BiConsumer<B,V> encoder, Function<B,V> decoder) {
        return new StreamCodec<>() {
            public V decode(B b) { return decoder.apply(b); }
            public void encode(B b,V v) { encoder.accept(b,v); }
        };
    }
    static <B,V> StreamCodec<B,V> ofMember(BiConsumer<V,B> encoder, Function<B,V> decoder) {
        return of((b,v) -> encoder.accept(v,b), decoder);
    }
    static <B,V> StreamCodec<B,V> unit(V value) { return of((b,v)->{}, b->value); }
    default <R> StreamCodec<B,R> map(Function<? super V,? extends R> from, Function<? super R,? extends V> to) {
        return of((b,v)->encode(b,to.apply(v)), b->from.apply(decode(b)));
    }
    @SuppressWarnings("unchecked") default <S extends B> StreamCodec<S,V> cast() { return (StreamCodec<S,V>) this; }
    default <R> StreamCodec<B,R> apply(Function<StreamCodec<B,V>, StreamCodec<B,R>> transform) { return transform.apply(this); }
    static <B,V,T1> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, Factory1<T1,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); }, b->factory.create(c1.decode(b)));
    }
    @FunctionalInterface interface Factory1<T1,V> { V create(T1 v1); }
    static <B,V,T1,T2> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, Factory2<T1,T2,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b)));
    }
    @FunctionalInterface interface Factory2<T1,T2,V> { V create(T1 v1,T2 v2); }
    static <B,V,T1,T2,T3> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, Factory3<T1,T2,T3,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b)));
    }
    @FunctionalInterface interface Factory3<T1,T2,T3,V> { V create(T1 v1,T2 v2,T3 v3); }
    static <B,V,T1,T2,T3,T4> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, StreamCodec<? super B,T4> c4, Function<V,T4> f4, Factory4<T1,T2,T3,T4,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); c4.encode(b,f4.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b),c4.decode(b)));
    }
    @FunctionalInterface interface Factory4<T1,T2,T3,T4,V> { V create(T1 v1,T2 v2,T3 v3,T4 v4); }
    static <B,V,T1,T2,T3,T4,T5> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, StreamCodec<? super B,T4> c4, Function<V,T4> f4, StreamCodec<? super B,T5> c5, Function<V,T5> f5, Factory5<T1,T2,T3,T4,T5,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); c4.encode(b,f4.apply(v)); c5.encode(b,f5.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b),c4.decode(b),c5.decode(b)));
    }
    @FunctionalInterface interface Factory5<T1,T2,T3,T4,T5,V> { V create(T1 v1,T2 v2,T3 v3,T4 v4,T5 v5); }
    static <B,V,T1,T2,T3,T4,T5,T6> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, StreamCodec<? super B,T4> c4, Function<V,T4> f4, StreamCodec<? super B,T5> c5, Function<V,T5> f5, StreamCodec<? super B,T6> c6, Function<V,T6> f6, Factory6<T1,T2,T3,T4,T5,T6,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); c4.encode(b,f4.apply(v)); c5.encode(b,f5.apply(v)); c6.encode(b,f6.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b),c4.decode(b),c5.decode(b),c6.decode(b)));
    }
    @FunctionalInterface interface Factory6<T1,T2,T3,T4,T5,T6,V> { V create(T1 v1,T2 v2,T3 v3,T4 v4,T5 v5,T6 v6); }
    static <B,V,T1,T2,T3,T4,T5,T6,T7> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, StreamCodec<? super B,T4> c4, Function<V,T4> f4, StreamCodec<? super B,T5> c5, Function<V,T5> f5, StreamCodec<? super B,T6> c6, Function<V,T6> f6, StreamCodec<? super B,T7> c7, Function<V,T7> f7, Factory7<T1,T2,T3,T4,T5,T6,T7,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); c4.encode(b,f4.apply(v)); c5.encode(b,f5.apply(v)); c6.encode(b,f6.apply(v)); c7.encode(b,f7.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b),c4.decode(b),c5.decode(b),c6.decode(b),c7.decode(b)));
    }
    @FunctionalInterface interface Factory7<T1,T2,T3,T4,T5,T6,T7,V> { V create(T1 v1,T2 v2,T3 v3,T4 v4,T5 v5,T6 v6,T7 v7); }
    static <B,V,T1,T2,T3,T4,T5,T6,T7,T8> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, StreamCodec<? super B,T4> c4, Function<V,T4> f4, StreamCodec<? super B,T5> c5, Function<V,T5> f5, StreamCodec<? super B,T6> c6, Function<V,T6> f6, StreamCodec<? super B,T7> c7, Function<V,T7> f7, StreamCodec<? super B,T8> c8, Function<V,T8> f8, Factory8<T1,T2,T3,T4,T5,T6,T7,T8,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); c4.encode(b,f4.apply(v)); c5.encode(b,f5.apply(v)); c6.encode(b,f6.apply(v)); c7.encode(b,f7.apply(v)); c8.encode(b,f8.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b),c4.decode(b),c5.decode(b),c6.decode(b),c7.decode(b),c8.decode(b)));
    }
    @FunctionalInterface interface Factory8<T1,T2,T3,T4,T5,T6,T7,T8,V> { V create(T1 v1,T2 v2,T3 v3,T4 v4,T5 v5,T6 v6,T7 v7,T8 v8); }
    static <B,V,T1,T2,T3,T4,T5,T6,T7,T8,T9> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, StreamCodec<? super B,T4> c4, Function<V,T4> f4, StreamCodec<? super B,T5> c5, Function<V,T5> f5, StreamCodec<? super B,T6> c6, Function<V,T6> f6, StreamCodec<? super B,T7> c7, Function<V,T7> f7, StreamCodec<? super B,T8> c8, Function<V,T8> f8, StreamCodec<? super B,T9> c9, Function<V,T9> f9, Factory9<T1,T2,T3,T4,T5,T6,T7,T8,T9,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); c4.encode(b,f4.apply(v)); c5.encode(b,f5.apply(v)); c6.encode(b,f6.apply(v)); c7.encode(b,f7.apply(v)); c8.encode(b,f8.apply(v)); c9.encode(b,f9.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b),c4.decode(b),c5.decode(b),c6.decode(b),c7.decode(b),c8.decode(b),c9.decode(b)));
    }
    @FunctionalInterface interface Factory9<T1,T2,T3,T4,T5,T6,T7,T8,T9,V> { V create(T1 v1,T2 v2,T3 v3,T4 v4,T5 v5,T6 v6,T7 v7,T8 v8,T9 v9); }
    static <B,V,T1,T2,T3,T4,T5,T6,T7,T8,T9,T10> StreamCodec<B,V> composite(StreamCodec<? super B,T1> c1, Function<V,T1> f1, StreamCodec<? super B,T2> c2, Function<V,T2> f2, StreamCodec<? super B,T3> c3, Function<V,T3> f3, StreamCodec<? super B,T4> c4, Function<V,T4> f4, StreamCodec<? super B,T5> c5, Function<V,T5> f5, StreamCodec<? super B,T6> c6, Function<V,T6> f6, StreamCodec<? super B,T7> c7, Function<V,T7> f7, StreamCodec<? super B,T8> c8, Function<V,T8> f8, StreamCodec<? super B,T9> c9, Function<V,T9> f9, StreamCodec<? super B,T10> c10, Function<V,T10> f10, Factory10<T1,T2,T3,T4,T5,T6,T7,T8,T9,T10,V> factory) {
        return of((b,v)->{ c1.encode(b,f1.apply(v)); c2.encode(b,f2.apply(v)); c3.encode(b,f3.apply(v)); c4.encode(b,f4.apply(v)); c5.encode(b,f5.apply(v)); c6.encode(b,f6.apply(v)); c7.encode(b,f7.apply(v)); c8.encode(b,f8.apply(v)); c9.encode(b,f9.apply(v)); c10.encode(b,f10.apply(v)); }, b->factory.create(c1.decode(b),c2.decode(b),c3.decode(b),c4.decode(b),c5.decode(b),c6.decode(b),c7.decode(b),c8.decode(b),c9.decode(b),c10.decode(b)));
    }
    @FunctionalInterface interface Factory10<T1,T2,T3,T4,T5,T6,T7,T8,T9,T10,V> { V create(T1 v1,T2 v2,T3 v3,T4 v4,T5 v5,T6 v6,T7 v7,T8 v8,T9 v9,T10 v10); }
}
