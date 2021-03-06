package org.jabref.logic.l10n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.fxml.FXMLLoader;

import com.sun.javafx.application.PlatformImpl;

public class LocalizationParser {

    public static SortedSet<LocalizationEntry> find(LocalizationBundleForTest type) throws IOException {
        Set<LocalizationEntry> entries = findLocalizationEntriesInFiles(type);

        Set<String> keysInJavaFiles = entries.stream()
                .map(LocalizationEntry::getKey)
                .distinct()
                .sorted()
                .collect(Collectors.toSet());

        Set<String> englishKeys;
        if (type == LocalizationBundleForTest.LANG) {
            englishKeys = getKeysInPropertiesFile("/l10n/JabRef_en.properties");
        } else {
            englishKeys = getKeysInPropertiesFile("/l10n/Menu_en.properties");
        }
        List<String> missingKeys = new LinkedList<>(keysInJavaFiles);
        missingKeys.removeAll(englishKeys);

        return entries.stream().filter(e -> missingKeys.contains(e.getKey())).collect(
                Collectors.toCollection(TreeSet::new));
    }

    public static SortedSet<String> findObsolete(LocalizationBundleForTest type) throws IOException {
        Set<LocalizationEntry> entries = findLocalizationEntriesInFiles(type);

        Set<String> keysInFiles = entries.stream().map(LocalizationEntry::getKey).collect(Collectors.toSet());

        Set<String> englishKeys;
        if (type == LocalizationBundleForTest.LANG) {
            englishKeys = getKeysInPropertiesFile("/l10n/JabRef_en.properties");
        } else {
            englishKeys = getKeysInPropertiesFile("/l10n/Menu_en.properties");
        }
        englishKeys.removeAll(keysInFiles);

        return englishKeys.stream().collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<LocalizationEntry> findLocalizationEntriesInFiles(LocalizationBundleForTest type) throws IOException {
        if (type == LocalizationBundleForTest.MENU) {
            return findLocalizationEntriesInJavaFiles(type);
        } else {
            Set<LocalizationEntry> entriesInFiles = new HashSet<>();
            entriesInFiles.addAll(findLocalizationEntriesInJavaFiles(type));
            entriesInFiles.addAll(findLocalizationEntriesInFxmlFiles(type));
            return entriesInFiles;
        }
    }

    private static Set<LocalizationEntry> findLocalizationEntriesInJavaFiles(LocalizationBundleForTest type)
            throws IOException {
        return Files.walk(Paths.get("src/main"))
                .filter(LocalizationParser::isJavaFile)
                .flatMap(path -> getLanguageKeysInJavaFile(path, type).stream())
                .collect(Collectors.toSet());
    }

    private static Set<LocalizationEntry> findLocalizationEntriesInFxmlFiles(LocalizationBundleForTest type)
            throws IOException {
        return Files.walk(Paths.get("src/main"))
                .filter(LocalizationParser::isFxmlFile)
                .flatMap(path -> getLanguageKeysInFxmlFile(path, type).stream())
                .collect(Collectors.toSet());
    }

    public static SortedSet<String> getKeysInPropertiesFile(String path) {
        Properties properties = getProperties(path);

        return properties.keySet().stream()
                .sorted()
                .map(Object::toString)
                .map(String::trim)
                .map(e -> new LocalizationKey(e).getPropertiesKey())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static Properties getProperties(String path) {
        Properties properties = new Properties();
        try (InputStream is = LocalizationConsistencyTest.class.getResourceAsStream(path);
                InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    private static boolean isJavaFile(Path path) {
        return path.toString().endsWith(".java");
    }

    private static boolean isFxmlFile(Path path) {
        return path.toString().endsWith(".fxml");
    }

    private static List<LocalizationEntry> getLanguageKeysInJavaFile(Path path, LocalizationBundleForTest type) {
        List<LocalizationEntry> result = new LinkedList<>();

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String content = String.join("\n", lines);

            List<String> keys = JavaLocalizationEntryParser.getLanguageKeysInString(content, type);

            for (String key : keys) {
                result.add(new LocalizationEntry(path, key, type));
            }

        } catch (IOException ignore) {
            ignore.printStackTrace();
        }

        return result;
    }

    /**
     * Loads the fxml file and returns all used language resources.
     */
    private static List<LocalizationEntry> getLanguageKeysInFxmlFile(Path path, LocalizationBundleForTest type) {
        List<String> result = new LinkedList<>();

        // Record which keys are requested; we pretend that we have all keys
        ResourceBundle registerUsageResourceBundle = new ResourceBundle() {

            @Override
            protected Object handleGetObject(String key) {
                result.add(key);
                return "test";
            }

            @Override
            public Enumeration<String> getKeys() {
                return null;
            }

            @Override
            public boolean containsKey(String key) {
                return true;
            }
        };

        try {
            PlatformImpl.startup(() -> {});
            FXMLLoader loader = new FXMLLoader(path.toUri().toURL(), registerUsageResourceBundle);
            loader.load();
        } catch (IOException ignore) {
            ignore.printStackTrace();
        }

        return result.stream()
                .map(key -> new LocalizationEntry(path, new LocalizationKey(key).getPropertiesKey(), type))
                .collect(Collectors.toList());
    }

    static class JavaLocalizationEntryParser {

        private static final String INFINITE_WHITESPACE = "\\s*";
        private static final String DOT = "\\.";
        private static final Pattern LOCALIZATION_START_PATTERN = Pattern.compile("Localization" + INFINITE_WHITESPACE + DOT + INFINITE_WHITESPACE + "lang" + INFINITE_WHITESPACE + "\\(");

        private static final Pattern LOCALIZATION_MENU_START_PATTERN = Pattern.compile("Localization" + INFINITE_WHITESPACE + DOT + INFINITE_WHITESPACE + "menuTitle" + INFINITE_WHITESPACE + "\\(");
        private static final Pattern ESCAPED_QUOTATION_SYMBOL = Pattern.compile("\\\\\"");

        private static final Pattern QUOTATION_SYMBOL = Pattern.compile("QUOTATIONPLACEHOLDER");

        public static List<String> getLanguageKeysInString(String content, LocalizationBundleForTest type) {
            List<String> result = new LinkedList<>();

            Matcher matcher;
            if (type == LocalizationBundleForTest.LANG) {
                matcher = LOCALIZATION_START_PATTERN.matcher(content);
            } else {
                matcher = LOCALIZATION_MENU_START_PATTERN.matcher(content);
            }
            while (matcher.find()) {
                // find contents between the brackets, covering multi-line strings as well
                int index = matcher.end();
                int brackets = 1;
                StringBuilder buffer = new StringBuilder();
                while (brackets != 0) {
                    char c = content.charAt(index);
                    if (c == '(') {
                        brackets++;
                    } else if (c == ')') {
                        brackets--;
                    }
                    buffer.append(c);
                    index++;
                }

                String parsedContentsOfLangMethod = ESCAPED_QUOTATION_SYMBOL.matcher(buffer.toString()).replaceAll("QUOTATIONPLACEHOLDER");

                // only retain what is within quotation
                StringBuilder b = new StringBuilder();
                int quotations = 0;
                for (char c : parsedContentsOfLangMethod.toCharArray()) {
                    if ((c == '"') && (quotations > 0)) {
                        quotations--;
                    } else if (c == '"') {
                        quotations++;
                    } else {
                        if (quotations != 0) {
                            b.append(c);
                        } else {
                            if (c == ',') {
                                break;
                            }
                        }
                    }
                }

                String languageKey = QUOTATION_SYMBOL.matcher(b.toString()).replaceAll("\\\"");

                // escape chars which are not allowed in property file keys
                String languagePropertyKey = new LocalizationKey(languageKey).getPropertiesKey();

                if (languagePropertyKey.endsWith("_")) {
                    throw new RuntimeException(languageKey + " ends with a space. As this is a localization key, this is illegal!");
                }

                if (languagePropertyKey.contains("\\n")) {
                    throw new RuntimeException(languageKey + " contains a new line character. As this is a localization key, this is illegal!");
                }

                if (!languagePropertyKey.trim().isEmpty()) {
                    result.add(languagePropertyKey);
                }

            }

            return result;
        }

    }

}
