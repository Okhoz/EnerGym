package com.energym.app.i18n;

import com.energym.app.R;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/** Resuelve los textos de Txt en los tests de JVM leyendo res/values/strings.xml (español). */
public final class SourceStrings implements Txt.Resolver {
	private static final Locale ES = Locale.forLanguageTag("es-ES");
	private final Map<String, String> strings = new HashMap<>();
	private final Map<String, Map<String, String>> plurals = new HashMap<>();
	private final Map<Integer, String> stringNames = names(R.string.class);
	private final Map<Integer, String> pluralNames = names(R.plurals.class);

	public SourceStrings() {
		try {
			File file = new File("src/main/res/values/strings.xml");
			if (!file.exists()) file = new File("app/src/main/res/values/strings.xml");
			Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getDocumentElement();
			NodeList list = root.getElementsByTagName("string");
			for (int i = 0; i < list.getLength(); i++) {
				Element e = (Element) list.item(i);
				strings.put(e.getAttribute("name"), unescape(e.getTextContent()));
			}
			NodeList ps = root.getElementsByTagName("plurals");
			for (int i = 0; i < ps.getLength(); i++) {
				Element p = (Element) ps.item(i);
				Map<String, String> items = new HashMap<>();
				NodeList children = p.getElementsByTagName("item");
				for (int j = 0; j < children.getLength(); j++) {
					Element item = (Element) children.item(j);
					items.put(item.getAttribute("quantity"), unescape(item.getTextContent()));
				}
				plurals.put(p.getAttribute("name"), items);
			}
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer strings.xml", e);
		}
	}

	@Override
	public String string(int id, Object... args) {
		String value = strings.get(stringNames.get(id));
		if (value == null) throw new IllegalArgumentException("Texto desconocido: " + id);
		return args.length == 0 ? value : String.format(ES, value, args);
	}

	@Override
	public String plural(int id, int count, Object... args) {
		Map<String, String> items = plurals.get(pluralNames.get(id));
		if (items == null) throw new IllegalArgumentException("Plural desconocido: " + id);
		String value = count == 1 && items.containsKey("one") ? items.get("one") : items.get("other");
		return String.format(ES, value, args.length == 0 ? new Object[]{count} : args);
	}

	@Override
	public Locale locale() {
		return ES;
	}

	private static Map<Integer, String> names(Class<?> type) {
		Map<Integer, String> names = new HashMap<>();
		for (Field field : type.getFields()) {
			try {
				names.put(field.getInt(null), field.getName());
			} catch (IllegalAccessException ignored) {
			}
		}
		return names;
	}

	private static String unescape(String raw) {
		String s = raw.trim();
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
		return s.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\@", "@").replace("\\?", "?");
	}
}
