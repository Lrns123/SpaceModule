/*
 * This file is part of SpaceModule (http://spacebukkit.xereo.net/).
 * 
 * SpaceModule is free software: you can redistribute it and/or modify it under the terms of the
 * Attribution-NonCommercial-ShareAlike Unported (CC BY-NC-SA) license as published by the Creative
 * Common organization, either version 3.0 of the license, or (at your option) any later version.
 * 
 * SpaceBukkit is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
 * Attribution-NonCommercial-ShareAlike Unported (CC BY-NC-SA) license for more details.
 * 
 * You should have received a copy of the Attribution-NonCommercial-ShareAlike Unported (CC BY-NC-SA)
 * license along with this program. If not, see <http://creativecommons.org/licenses/by-nc-sa/3.0/>.
 */
package me.neatmonster.spacemodule.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class ActionsManager {
    protected LinkedHashMap<String, Method> actions = new LinkedHashMap<String, Method>();

    public Object cast(final Object object, final Class<?> current, final Class<?> expected) {
        try {
            if (expected == Object[].class)
                if (current.isArray())
                    return Arrays.asList(object).toArray();
                else if (current.isAssignableFrom(List.class))
                    return ((List<?>) object).toArray();
                else
                    return new Object[] {object};
            final String string = object.toString();
            if (expected == String.class)
                return string;
            else if (expected == Character.class || expected == char.class)
                return string.charAt(0);
            else if (expected == Byte.class || expected == byte.class)
                return Byte.parseByte(string);
            else if (expected == Short.class || expected == short.class)
                return Short.parseShort(string);
            else if (expected == Integer.class || expected == int.class)
                return Integer.parseInt(string);
            else if (expected == Long.class || expected == long.class)
                return Long.parseLong(string);
            else if (expected == Float.class || expected == float.class)
                return Float.parseFloat(string);
            else if (expected == Double.class || expected == double.class)
                return Double.parseDouble(string);
            else if (expected == Boolean.class || expected == boolean.class)
                return Boolean.parseBoolean(string);
            return null;
        } catch (final Exception e) {
            return null;
        }
    }

    public boolean contains(final String alias) {
        return actions.containsKey(alias.toLowerCase());
    }

    public Object execute(final String alias, final Object... arguments) throws InvalidArgumentsException,
            UnhandledActionException {
        final Method method = actions.get(alias.toLowerCase());
        if (method == null)
            throw new UnhandledActionException();
        for (int a = 0; a < method.getParameterTypes().length; a++)
            try {
                if (!arguments[a].getClass().getName().equals(method.getParameterTypes()[a].getName())) {
                    final Object casted = cast(arguments[a], arguments[a].getClass(), method.getParameterTypes()[a]);
                    if (casted == null)
                        throw new InvalidArgumentsException(method.getParameterTypes()[a].getSimpleName() + " ("
                                + method.getParameterTypes()[a].getName() + ") expected, not "
                                + arguments[a].getClass().getSimpleName() + " (" + arguments[a].getClass().getName()
                                + ") for method " + method.getName() + ".");
                    else
                        arguments[a] = casted;
                }
            } catch (final ArrayIndexOutOfBoundsException e) {
                throw new InvalidArgumentsException(method.getParameterTypes().length + " arguments expected, not "
                        + arguments.length + " for method " + method.getName() + ".");
            }
        return invoke(method, arguments);
    }

    protected Object invoke(final Method method, final Object... arguments) {
        try {
            return method.invoke(method.getDeclaringClass().newInstance(), arguments);
        } catch (final IllegalArgumentException e) {
            e.printStackTrace();
        } catch (final IllegalAccessException e) {
            e.printStackTrace();
        } catch (final InvocationTargetException e) {
            e.printStackTrace();
        } catch (final SecurityException e) {
            e.printStackTrace();
        } catch (final InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isSchedulable(final String alias) throws UnhandledActionException {
        final Method method = actions.get(alias.toLowerCase());
        if (method == null)
            throw new UnhandledActionException();
        final Action action = method.getAnnotation(Action.class);
        return action.schedulable();
    }

    public void register(final Class<?> class_) {
        for (final Method method : class_.getMethods()) {
            if (!method.isAnnotationPresent(Action.class))
                continue;
            final Action action = method.getAnnotation(Action.class);
            for (final String alias : action.aliases())
                actions.put(alias.toLowerCase(), method);
        }
    }
}