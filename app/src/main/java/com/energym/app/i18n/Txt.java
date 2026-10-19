package com.energym.app.i18n;

import android.content.res.Resources;

import java.util.Locale;

/**
 * Textos visibles traducibles desde cualquier capa (también la lógica sin Context).
 * En la app se resuelven con los recursos de Android (values/ y values-en/); en los tests de JVM,
 * con el strings.xml del código fuente, así los tests siguen comparando el texto en español.
 */
public final class Txt {
	public interface Resolver {
		String string(int id, Object... args);

		String plural(int id, int count, Object... args);

		Locale locale();
	}

	private static volatile Resolver resolver = fallback();

	private Txt() {
	}

	public static void init(Resources resources) {
		resolver = new Resolver() {
			@Override
			public String string(int id, Object... args) {
				return args.length == 0 ? resources.getString(id) : resources.getString(id, args);
			}

			@Override
			public String plural(int id, int count, Object... args) {
				return resources.getQuantityString(id, count, args.length == 0 ? new Object[]{count} : args);
			}

			@Override
			public Locale locale() {
				return resources.getConfiguration().getLocales().get(0);
			}
		};
	}

	/** Solo para las pruebas de JVM: otro origen de textos (por ejemplo, el de values-en). */
	static void use(Resolver other) {
		resolver = other;
	}

	public static String t(int id, Object... args) {
		return resolver.string(id, args);
	}

	/** Plural; sin argumentos extra, el número se usa como %1$d. */
	public static String n(int id, int count, Object... args) {
		return resolver.plural(id, count, args);
	}

	public static Locale locale() {
		return resolver.locale();
	}

	/** La app se muestra en inglés: los datos de la app (ejercicios, alimentos) usan su versión en inglés. */
	public static boolean english() {
		Resolver r = resolver;
		return r != null && "en".equals(r.locale().getLanguage());
	}

	/**
	 * Número ya escrito con punto decimal («82.5») en la forma del idioma: coma en español, punto en inglés.
	 * Solo para mostrar; lo que se guarda o se lee siempre acepta los dos.
	 */
	public static String decimal(String number) {
		return english() ? number : number.replace('.', ',');
	}

	private static Resolver fallback() {
		try {
			return (Resolver) Class.forName("com.energym.app.i18n.SourceStrings").getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError missing) {
			return null;
		}
	}
}
