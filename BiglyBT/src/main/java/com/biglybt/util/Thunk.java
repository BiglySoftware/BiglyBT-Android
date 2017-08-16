/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.util;

import java.lang.annotation.*;

/**
 * See https://android.googlesource.com/platform/packages/apps/Launcher3/+/master/src/com/android/launcher3/util/Thunk.java
 * <p>
 * Until we have that Thunk, we can fake it by modifying .idea/misc.txt 's EntryPointsManager to ignore Inspections on this annotation.
 * The big caveat is that this will supress all inspections for the target, even handy ones like Unused Declaration.
 * Example of .idea/misc.txt:
 *
 * <pre>
 * &lt;component name="EntryPointsManager">
 *   &lt;entry_points version="2.0" />
 *   &lt;list size="1">
 *     &lt;item index="0" class="java.lang.String" itemvalue="Thunk" />
 *   &lt;/list>
 * &lt;/component>
 * </pre>
 * <p>
 * Below is the Javadoc from com.android.launcher3.util.Thunk:
 * <p>
 *
 * Indicates that the given field or method has package visibility solely to prevent the creation
 * of a synthetic method. In practice, you should treat this field/method as if it were private.
 * <p>
 *
 * When a private method is called from an inner class, the Java compiler generates a simple
 * package private shim method that the class generated from the inner class can call. This results
 * in unnecessary bloat and runtime method call overhead. It also gets us closer to the dex method
 * count limit.
 * <p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({
	ElementType.METHOD,
	ElementType.FIELD,
	ElementType.CONSTRUCTOR,
	ElementType.TYPE
})
public @interface Thunk {
}
