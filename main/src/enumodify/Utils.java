package enumodify;

import arc.struct.*;
import arc.util.*;

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.*;
import java.lang.reflect.*;

@SuppressWarnings("unchecked")
public final class Utils {
    private static MethodHandle MODIFIERS;
    private static MethodHandle DECL_CLASS;
    private static MethodHandle SET_ACCESSOR;

    private static boolean USE_SUN;
    private static Object J8_REF_FACTORY;
    private static MethodHandle J8_NEW_CONS_ACCESSOR;
    private static MethodHandle J8_NEW_INSTANCE;

    static {
        try {
            boolean use = false;
            try {
                Method getReflectionFactory = Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("getReflectionFactory");
                J8_REF_FACTORY = getReflectionFactory.invoke(null);

                Lookup lookup = MethodHandles.lookup();
                J8_NEW_CONS_ACCESSOR = lookup.unreflect(Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("newConstructorAccessor", Constructor.class));
                J8_NEW_INSTANCE = lookup.unreflect(Class.forName("sun.reflect.ConstructorAccessor").getDeclaredMethod("newInstance", Object[].class));

                use = true;
            } catch(Exception e) {
                use = false;
            }
            USE_SUN = use;

            Constructor<Lookup> cons = Lookup.class.getDeclaredConstructor(Class.class);
            cons.setAccessible(true);

            Field name = Class.class.getDeclaredField("name");
            name.setAccessible(true);

            name.set(Field.class, "Field");
            MODIFIERS = cons.newInstance(Field.class).findSetter(Field.class, "modifiers", int.class);
            name.set(Field.class, "java.lang.reflect.Field");

            name.set(Constructor.class, "Constructor");
            DECL_CLASS = cons.newInstance(Constructor.class).findSetter(Constructor.class, "clazz", Class.class);
            name.set(Constructor.class, "java.lang.reflect.Constructor");

            Method setf = null;
            Method[] methodsf = Field.class.getDeclaredMethods();
            for(Method method : methodsf) {
                if(method.getName().contains("setFieldAccessor")) {
                    setf = method;
                    break;
                }
            }

            name.set(Field.class, "Field");
            SET_ACCESSOR = cons.newInstance(Field.class).unreflect(setf);
            name.set(Field.class, "java.lang.reflect.Field");

            Log.info("Reflection/invocation utility has been initialized.");
            Log.info("Usage of sun packages: @.", USE_SUN);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Object getConstructorAccessor(Class<T> enumClass, Constructor<T> constructor) {
        try {
            return J8_NEW_CONS_ACCESSOR.invoke(J8_REF_FACTORY, constructor);
        } catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void revoke(Field field, int modifier) {
        try {
            field.setAccessible(true);

            int mods = field.getModifiers();
            MODIFIERS.invoke(field, mods & ~modifier);

            SET_ACCESSOR.invoke(field, null, false);
            SET_ACCESSOR.invoke(field, null, true);
        } catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T createEnum(Class<T> type, Constructor<T> cons, Object... args) {
        try {
            if(USE_SUN){
                return type.cast(J8_NEW_INSTANCE.invoke(getConstructorAccessor(type, cons), args));
            }else{
                cons.setAccessible(true);

                Class<T> before = cons.getDeclaringClass();
                DECL_CLASS.invoke(cons, Object.class);

                T obj = cons.newInstance(args);
                DECL_CLASS.invoke(cons, before);

                return obj;
            }
        } catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T addEntry(Class<T> type, String name) {
        if(!Enum.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("must be enum");
        }

        Field valuesField = null;
        Field[] fields = type.getDeclaredFields();
        for(Field field : fields) {
            if(field.getName().contains("$VALUES")) {
                valuesField = field;
                break;
            }
        }

        if(valuesField == null) throw new IllegalStateException("values field is null");
        try {
            valuesField.setAccessible(true);

            T[] previousValues = (T[])valuesField.get(type);
            Seq<T> values = Seq.with(previousValues);

            Constructor<T> cons = type.getDeclaredConstructor(String.class, int.class);
            T value = createEnum(type, cons, name, previousValues.length);
            values.add(value);

            revoke(valuesField, Modifier.FINAL);

            T[] array = (T[])Array.newInstance(type, values.size);
            for(int i = 0; i < values.size; i++) {
                array[i] = values.get(i);
            }

            valuesField.set(null, array);

            return value;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
