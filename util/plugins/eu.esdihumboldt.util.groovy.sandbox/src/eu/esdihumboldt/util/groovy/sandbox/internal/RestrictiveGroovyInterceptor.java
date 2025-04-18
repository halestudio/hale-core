/*
 * Copyright (c) 2013 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.groovy.sandbox.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import org.codehaus.groovy.runtime.GStringImpl;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;

import eu.esdihumboldt.util.groovy.sandbox.GroovyRestrictionException;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.IntRange;
import groovy.lang.MissingPropertyException;
import groovy.lang.Range;
import groovy.lang.Script;

/**
 * {@link GroovyInterceptor} which allows some basic classes but is pretty
 * restrictive. Constructor offers parameters to allow more classes.
 *
 * @author Kai Schwierczek
 */
public class RestrictiveGroovyInterceptor extends GroovyInterceptor {

	/**
	 * Classes, which may be initialized, and all their methods may be used.
	 */
	private static final Set<Class<?>> allowedClasses = new HashSet<>();
	/**
	 * Classes, which may be initialized, and all their methods, and method
	 * missing/getProperty() may be used.
	 */
	private static final Set<Class<?>> allAllowedClasses = new HashSet<>();

	/**
	 * Generally disallowed methods.
	 */
	private static final Set<String> disallowedMethods = new HashSet<>();
	/**
	 * Generally disallowed properties (write access).
	 */
	private static final Set<String> disallowedWriteProperties = new HashSet<>();

	/**
	 * Disallowed Script methods.
	 */
	private static final Set<String> disallowedScriptMethods = new HashSet<>();
	/**
	 * Disallowed Script properties (write access).
	 */
	private static final Set<String> disallowedScriptWriteProperties = new HashSet<>();

	/**
	 * Disallowed Closure methods.
	 */
	private static final Set<String> disallowedClosureMethods = new HashSet<>();
	/**
	 * Disallowed Closure properties (write access).
	 */
	private static final Set<String> disallowedClosureWriteProperties = new HashSet<>();

	/**
	 * Allowed packages.
	 */
	private static final List<AllowedPrefix> allowedPackages = new ArrayList<>();

	static {
		// standard classes
		allowedClasses.add(String.class);
		allowedClasses.add(Byte.class);
		allowedClasses.add(Short.class);
		allowedClasses.add(Integer.class);
		allowedClasses.add(Long.class);
		allowedClasses.add(Float.class);
		allowedClasses.add(Double.class);
		allowedClasses.add(BigInteger.class);
		allowedClasses.add(BigDecimal.class);
		allowedClasses.add(Math.class);
		allowedClasses.add(Date.class);
		allowedClasses.add(Locale.class);
		allowedClasses.add(Boolean.class);

		// Java 8 date/time classes
		allowedClasses.add(DayOfWeek.class);
		allowedClasses.add(Month.class);
		allowedClasses.add(Year.class);
		allowedClasses.add(YearMonth.class);
		allowedClasses.add(Instant.class);

		allowedClasses.add(LocalDate.class);
		allowedClasses.add(LocalDateTime.class);
		allowedClasses.add(LocalTime.class);
		allowedClasses.add(ZonedDateTime.class);
		allowedClasses.add(ZoneId.class);

		allowedClasses.add(DateTimeFormatter.class);
		allowedClasses.add(DateTimeFormatterBuilder.class);
		allowedClasses.add(FormatStyle.class);
		allowedClasses.add(TextStyle.class);
		allowedClasses.add(SignStyle.class);

		// regular expressions
		allowedClasses.add(Pattern.class);

		// helper classes
		allowedClasses.add(SimpleDateFormat.class);
		allowedClasses.add(UUID.class);

		// Collections & Classes used by Groovy
		allowedClasses.add(LinkedHashMap.class);
		allowedClasses.add(LinkedHashSet.class);
		allowedClasses.add(ArrayList.class);
		allowedClasses.add(Range.class);
		allowedClasses.add(IntRange.class);
		allowedClasses.add(GStringImpl.class);

		// Some more collections
		allowedClasses.add(HashMap.class);
		allowedClasses.add(HashSet.class);
		allowedClasses.add(Collections.class);
		allowedClasses.add(TreeMap.class);

		// Binding classes
		allowedClasses.add(Timestamp.class);
		allowedClasses.add(java.sql.Date.class);

		// Number formatting
		allowedClasses.add(NumberFormat.class);
		allowedClasses.add(DecimalFormat.class);
		allowedClasses.add(DecimalFormatSymbols.class);

		// Exception classes
		allowedClasses.add(Throwable.class);

		// Google collect
		allowedPackages.add(new AllowedPrefix("com.google.common.collect", true));

		// general disallow access to specific Groovy methods
		disallowedMethods.add("invokeMethod");
		disallowedMethods.add("setMetaClass");
		disallowedMethods.add("setProperty");
		disallowedWriteProperties.add("metaClass");

		// forbid self-execution of script and overwriting of binding
		disallowedScriptMethods.add("run");
		disallowedScriptMethods.add("evaluate");
		disallowedScriptMethods.add("setBinding");
		disallowedScriptWriteProperties.add("binding");

		// forbid explicit setting of delegate, resolve strategy and directive
		disallowedClosureMethods.add("setDelegate");
		disallowedClosureMethods.add("setResolveStrategy");
		disallowedClosureMethods.add("setDirective");
		// and rehydrate as that results basically in setting the owner,
		// delegate and thisObject
		disallowedClosureMethods.add("rehydrate");
		disallowedClosureWriteProperties.add("delegate");
		disallowedClosureWriteProperties.add("resolveStrategy");
		disallowedClosureWriteProperties.add("directive");
	}

	/**
	 * AllowedPackage saves information to allow the use of all classes with a given
	 * prefix.
	 */
	public static class AllowedPrefix {

		private final String prefix;
		private final boolean allowChildren;

		/**
		 * Default constructor which accepts a package name.
		 *
		 * @param prefix the package prefix (final '.' is added if not present)
		 * @param allowChildren whether child-packages are allowed, too
		 */
		public AllowedPrefix(String prefix, boolean allowChildren) {
			if (!prefix.endsWith("."))
				prefix += '.';
			this.prefix = prefix;
			this.allowChildren = allowChildren;
		}

		/**
		 * Checks whether the given class is allowed to be used because of this allowed
		 * package.
		 *
		 * @param clazz the class to test
		 * @return true, if the class may be used, false otherwise
		 */
		public boolean checkAllowed(Class<?> clazz) {
			String className = clazz.getName();

			if (className.startsWith(prefix)) {
				return allowChildren || !className.substring(prefix.length()).contains(".");
			}
			else {
				return false;
			}
		}
	}

	private final Set<Class<?>> instanceAllowedClasses = new HashSet<>(allowedClasses);
	private final Set<Class<?>> instanceAllAllowedClasses = new HashSet<>(allAllowedClasses);
	private final List<AllowedPrefix> instanceAllowedPackages = new ArrayList<>(allowedPackages);

	/**
	 * Constructor using additional allowed classes.
	 *
	 * @param additionalAllowedClasses classes, which may be initialized, and all
	 *            their declared methods may be used
	 * @param additionalAllAllowedClasses classes, which may be initialized, and any
	 *            call on them is allowed (has to implement methodMissing or equal)
	 * @param additionalAllowedPackages packages whose classes and their declared
	 *            methods may be used
	 */
	public RestrictiveGroovyInterceptor(Set<Class<?>> additionalAllowedClasses,
			Set<Class<?>> additionalAllAllowedClasses,
			List<AllowedPrefix> additionalAllowedPackages) {
		instanceAllowedClasses.addAll(additionalAllowedClasses);
		instanceAllowedClasses.addAll(additionalAllAllowedClasses);
		instanceAllAllowedClasses.addAll(additionalAllAllowedClasses);
		instanceAllowedPackages.addAll(additionalAllowedPackages);
	}

	@Override
	public Object onStaticCall(Invoker invoker, @SuppressWarnings("rawtypes") Class receiver,
			String method, Object... args) throws Throwable {
		if (isAllowedClass(receiver) || isScriptClass(receiver))
			return super.onStaticCall(invoker, receiver, method, args);
		else
			throw new GroovyRestrictionException(
					"using class " + receiver.getName() + " is not allowed!");
	}

	@Override
	public Object onNewInstance(Invoker invoker, @SuppressWarnings("rawtypes") Class receiver,
			Object... args) throws Throwable {
		// classes defined in the script would be okay, sadly it is not possible
		// to identify those?
		if (isAllowedClass(receiver) || isScriptClass(receiver))
			return super.onNewInstance(invoker, receiver, args);
		else
			throw new GroovyRestrictionException(
					"using class " + receiver.getName() + " is not allowed!");
	}

	@Override
	public Object onMethodCall(Invoker invoker, Object receiver, String method, Object... args)
			throws Throwable {
		if (disallowedMethods.contains(method))
			throw new GroovyRestrictionException(
					"using methods named " + method + " is not allowed in Groovy transformations!");
		else if (receiver instanceof Closure && disallowedClosureMethods.contains(method))
			throw new GroovyRestrictionException("using the closure method " + method
					+ " is not allowed in Groovy transformations!");
		// Return value doesn't matter!
		// true -> allowed delegation found
		// false -> no disallowed delegation found
		checkMethodCall(receiver, method);
		return super.onMethodCall(invoker, receiver, method, args);
	}

	private boolean checkMethodCall(Object receiver, String method)
			throws GroovyRestrictionException {
		if (receiver instanceof Closure) {
			// Closure method names were tested before.
			Closure<?> closure = (Closure<?>) receiver;
			Object owner = closure.getOwner();
			Object delegate = closure.getDelegate();
			int rs = closure.getResolveStrategy();
			// Check owner first.
			if (rs == Closure.OWNER_FIRST || rs == Closure.OWNER_ONLY)
				if (checkMethodCall(owner, method))
					return true;
			// Check delegate first/second.
			if (rs == Closure.OWNER_FIRST || rs == Closure.DELEGATE_FIRST
					|| rs == Closure.DELEGATE_ONLY)
				if (delegate != null && delegate != closure)
					if (checkMethodCall(delegate, method))
						return true;
			// Check owner second.
			if (rs == Closure.DELEGATE_FIRST)
				if (checkMethodCall(owner, method))
					return true;

			// Cannot be 100% sure whether the call will be handled by
			// delegation to this closure.
			return false;
		}
		else if (isAllowedClass(receiver.getClass())) {
			checkExecute(receiver, method);
			return instanceAllAllowedClasses.contains(receiver.getClass())
					|| !InvokerHelper.getMetaClass(receiver).respondsTo(receiver, method).isEmpty();
		}
		else if (isScriptClass(receiver.getClass()) && !disallowedScriptMethods.contains(method))
			return !InvokerHelper.getMetaClass(receiver).respondsTo(receiver, method).isEmpty();
		throw new GroovyRestrictionException("Possible access of method " + method + " on class "
				+ receiver.getClass().getName() + " is not allowed in Groovy transformations!");
	}

	/**
	 * Checks for an execute call on List, String, String[] and GString.
	 *
	 * @param receiver the receiver object
	 * @param method the method name
	 */
	private void checkExecute(Object receiver, String method) {
		if ("execute".equals(method)) {
			if (receiver instanceof List || receiver instanceof String
					|| receiver.getClass().isArray() || receiver instanceof String[]
					|| receiver instanceof GString) {
				throw new GroovyRestrictionException(
						"Possible access of method execute on List, String, String[] and GString is not allowed in Groovy transformations!");
			}
		}
	}

	private boolean isScriptClass(Class<?> receiver) {
		// while-doesn't really do anything, because Groovy extracts classes
		// defined in scripts as stand-alone classes.
//		while (receiver.getEnclosingClass() != null)
//			receiver = receiver.getEnclosingClass();
		return Script.class.isAssignableFrom(receiver);
	}

	@Override
	public Object onGetProperty(Invoker invoker, Object receiver, String property)
			throws Throwable {
		if (receiver instanceof Class<?> && isAllowedClass((Class<?>) receiver)
				&& !"class".equals(property))
			return super.onGetProperty(invoker, receiver, property);
		checkPropertyAccess(receiver, property, false);
		return super.onGetProperty(invoker, receiver, property);
	}

	@Override
	public Object onSetProperty(Invoker invoker, Object receiver, String property, Object value)
			throws Throwable {
		if (disallowedWriteProperties.contains(property))
			throw new GroovyRestrictionException("setting the property " + property
					+ " is not allowed in Groovy transformations!");
		if (receiver instanceof Closure && disallowedClosureWriteProperties.contains(property))
			throw new GroovyRestrictionException("setting the closure property " + property
					+ " is not allowed in Groovy transformations!");
		checkPropertyAccess(receiver, property, true);
		return super.onSetProperty(invoker, receiver, property, value);
	}

	private boolean checkPropertyAccess(Object receiver, String property, boolean set)
			throws GroovyRestrictionException {
		if (receiver instanceof Closure) {
			// Closure properties were tested before.
			Closure<?> closure = (Closure<?>) receiver;
			Object owner = closure.getOwner();
			Object delegate = closure.getDelegate();
			int rs = closure.getResolveStrategy();
			// Check owner first.
			if (rs == Closure.OWNER_FIRST || rs == Closure.OWNER_ONLY)
				if (checkPropertyAccess(owner, property, set))
					return true;
			// Check delegate first/second.
			if (rs == Closure.OWNER_FIRST || rs == Closure.DELEGATE_FIRST
					|| rs == Closure.DELEGATE_ONLY)
				if (delegate != null && delegate != closure)
					if (checkPropertyAccess(delegate, property, set))
						return true;
			// Check owner second.
			if (rs == Closure.DELEGATE_FIRST)
				if (checkPropertyAccess(owner, property, set))
					return true;
			// Cannot be 100% sure whether the property will be handled by
			// delegation to this closure.
			return false;
		}
		else if (instanceAllAllowedClasses.contains(receiver.getClass()))
			return true;
		else if (isAllowedClass(receiver.getClass()))
			return hasProperty(receiver, property);
		else if (isScriptClass(receiver.getClass())
				&& (!set || !disallowedScriptWriteProperties.contains(property)))
			return hasProperty(receiver, property);
		throw new GroovyRestrictionException("Possible " + (set ? "write " : "")
				+ "access of property " + property + " on class " + receiver.getClass().getName()
				+ " is not allowed in Groovy transformations!");
	}

	@Override
	public Object onGetAttribute(Invoker invoker, Object receiver, String attribute)
			throws Throwable {
		checkPropertyAccess(receiver, attribute, false);
		return super.onGetAttribute(invoker, receiver, attribute);
	}

	@Override
	public Object onSetAttribute(Invoker invoker, Object receiver, String attribute, Object value)
			throws Throwable {
		if (disallowedWriteProperties.contains(attribute))
			throw new GroovyRestrictionException("setting the property " + attribute
					+ " is not allowed in Groovy transformations!");
		if (receiver instanceof Closure && disallowedClosureWriteProperties.contains(attribute))
			throw new GroovyRestrictionException("setting the closure property " + attribute
					+ " is not allowed in Groovy transformations!");
		checkPropertyAccess(receiver, attribute, true);
		return super.onSetAttribute(invoker, receiver, attribute, value);
	}

	@Override
	public Object onGetArray(Invoker invoker, Object receiver, Object index) throws Throwable {
		// generally allow array access for now
		return super.onGetArray(invoker, receiver, index);
	}

	@Override
	public Object onSetArray(Invoker invoker, Object receiver, Object index, Object value)
			throws Throwable {
		// generally allow array access for now
		return super.onSetArray(invoker, receiver, index, value);
	}

	private static boolean hasProperty(Object object, String property) {
		if (InvokerHelper.getMetaClass(object).hasProperty(object, property) != null)
			return true;

		// The only way to be sure whether something is handled as a property in
		// Groovy is to actually get it and catch a MissingPropertyException.
		// But this actually accesses the property (-> side effects?)!
		// Here this is no problem, since we only disallow some write access...

		// The only allowed class with side effects should be InstanceAccessor,
		// which is in "allAllowedClasses" and thus shouldn't reach here

		try {
			InvokerHelper.getProperty(object, property);
			return true;
		} catch (MissingPropertyException e) {
			return false;
		}
	}

	private boolean isAllowedClass(Class<?> clazz) {
		// instanceAllowedClasses.add needs to be synchronized, as internal
		// state changes.
		// .contains does not need to be synchronized, worst case would be that
		// an element is added several times then, which doesn't matter.

		if (instanceAllowedClasses.contains(clazz))
			return true;

		// allow accessing arrays in general
		// (calls like execute are disallowed by another mechanism)
		if (clazz.isArray())
			return true;

		// allow nested classes of allowed classes
		Class<?> topLevelClass = clazz;
		while (topLevelClass.getEnclosingClass() != null) {
			topLevelClass = topLevelClass.getEnclosingClass();
		}
		if (topLevelClass != clazz) {
			if (instanceAllowedClasses.contains(topLevelClass)) {
				// cache result for nested class
				synchronized (instanceAllowedClasses) {
					instanceAllowedClasses.add(clazz);
				}
				return true;
			}
		}

		// walk through prefixes
		for (AllowedPrefix allowedPackage : instanceAllowedPackages) {
			if (allowedPackage.checkAllowed(clazz)) {
				// cache result for class within a allowed package
				synchronized (instanceAllowedClasses) {
					instanceAllowedClasses.add(clazz);
				}
				return true;
			}
		}
		return false;
	}
}
