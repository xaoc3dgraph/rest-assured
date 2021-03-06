/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jayway.restassured.path.json;

import com.jayway.restassured.assertion.JSONAssertion;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.exception.ParsePathException;
import com.jayway.restassured.internal.mapping.ObjectMapping;
import com.jayway.restassured.internal.support.Prettifier;
import com.jayway.restassured.mapper.ObjectMapperType;
import com.jayway.restassured.mapper.factory.GsonObjectMapperFactory;
import com.jayway.restassured.mapper.factory.Jackson1ObjectMapperFactory;
import com.jayway.restassured.mapper.factory.ObjectMapperFactory;
import com.jayway.restassured.parsing.Parser;
import com.jayway.restassured.response.ResponseBodyData;
import groovy.json.JsonBuilder;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.apache.commons.lang3.Validate;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

import static com.jayway.restassured.assertion.AssertParameter.notNull;
import static com.jayway.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static com.jayway.restassured.internal.path.ObjectConverter.convertObjectTo;

/**
 * JsonPath is an alternative to using XPath for easily getting values from a Object document. It follows the
 * Groovy dot notation syntax when getting an object from the document. You can regard it as an alternative to XPath for XML.
 * E.g. given the following Object document:
 * <pre>
 * { "store": {
 *   "book": [
 *    { "category": "reference",
 *      "author": "Nigel Rees",
 *      "title": "Sayings of the Century",
 *      "price": 8.95
 *    },
 *    { "category": "fiction",
 *      "author": "Evelyn Waugh",
 *      "title": "Sword of Honour",
 *      "price": 12.99
 *    },
 *    { "category": "fiction",
 *      "author": "Herman Melville",
 *      "title": "Moby Dick",
 *      "isbn": "0-553-21311-3",
 *      "price": 8.99
 *    },
 *    { "category": "fiction",
 *      "author": "J. R. R. Tolkien",
 *      "title": "The Lord of the Rings",
 *      "isbn": "0-395-19395-8",
 *      "price": 22.99
 *    }
 *  ],
 *    "bicycle": {
 *      "color": "red",
 *      "price": 19.95
 *    }
 *  }
 * }
 * </pre>
 * To get a list of all book categories:
 * <pre>
 * List&lt;String&gt; categories = with(Object).get("store.book.category");
 * </pre>
 *
 * Get the first book category:
 * <pre>
 * String category = with(Object).get("store.book[0].category");
 * </pre>
 *
 * Get the last book category:
 * <pre>
 * String category = with(Object).get("store.book[-1].category");
 * </pre>
 *
 * Get all books with price between 5 and 15:
 * <pre>
 * List&lt;Map&gt; books = with(Object).get("store.book.findAll { book -> book.price >= 5 && book.price <= 15 }");
 * </pre>
 *
 */
public class JsonPath {

    private final Object json;
    private String rootPath = "";
    private ObjectMapperFactory<?> objectMapperFactory;

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param text The text containing the Object document
     */
    public JsonPath(String text) {
        json = new JsonSlurper().parseText(text);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param url The url containing the Object document
     */
    public JsonPath(URL url) {
        json = parseURL(url);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param stream The stream containing the Object document
     */
    public JsonPath(InputStream stream) {
        json = parseInputStream(stream);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param file The file containing the Object document
     */
    public JsonPath(File file) {
        json = parseFile(file);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param reader The reader containing the Object document
     */
    public JsonPath(Reader reader) {
        json = parseReader(reader);
    }

    private JsonPath(JsonPath jsonPath, ObjectMapperFactory<?> objectMapperFactory) {
        Validate.notNull(objectMapperFactory, "Object mapper factory cannot be null");
        this.objectMapperFactory = objectMapperFactory;
        this.json = jsonPath.json;
        this.rootPath = jsonPath.rootPath;
    }

    /**
     * Get a Object graph with no named root element as a Java object. This is just a short-cut for
     *
     * <pre>
     *     get("");
     * </pre>
     * or
     * <pre>
     *     get("$");
     * </pre>
     *
     * @return The object matching the Object graph. This may be any primitive type, a List or a Map.  A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <T> T get() {
        return get("");
    }

    /**
     * Get the result of an Object path expression as a boolean.
     *
     * @param path The Object path.
     * @return The object matching the Object path. This may be any primitive type, a List or a Map.  A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <T> T get(String path) {
        final JSONAssertion jsonAssertion = createJsonAssertion(path);
        return (T) jsonAssertion.getResult(json);
    }

    /**
     * Get the result of an Object path expression as a boolean
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public boolean getBoolean(String path) {
        return convertObjectTo(get(path), Boolean.class);
    }

    /**
     * Get the result of an Object path expression as a char.
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public char getChar(String path) {
        return convertObjectTo(get(path), Character.class);
    }

    /**
     * Get the result of an Object path expression as an int.
     *
     * @param path The Object path.
     * @return The int matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public int getInt(String path) {
        //The type returned from Groovy depends on the input, so we need to handle different numerical types.
        Object value = get(path);
        if(value instanceof Integer) {
            return (Integer) value;
        }else  if (value instanceof Short) {
            return ((Short)value).intValue();
        } else if (value instanceof Long) {
            return ((Long)value).intValue();
        } else {
            return convertObjectTo(value, Integer.class);
        }
    }

    /**
     * Get the result of an Object path expression as a byte.
     *
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public byte getByte(String path) {
        //The type returned from Groovy depends on the input, so we need to handle different numerical types.
        Object value = get(path);
        if(value instanceof Byte) {
            return (Byte) value;
        } else if (value instanceof Long) {
            return ((Long)value).byteValue();
        } else if (value instanceof Integer) {
            return ((Integer)value).byteValue();
        } else {
            return convertObjectTo(value, Byte.class);
        }
    }

    /**
     * Get the result of an Object path expression as a short.
     *
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public short getShort(String path) {
        //The type returned from Groovy depends on the input, so we need to handle different numerical types.
        Object value = get(path);
        if(value instanceof Short) {
            return (Short) value;
        } else if (value instanceof Long) {
            return ((Long)value).shortValue();
        } else if (value instanceof Integer) {
            return ((Integer)value).shortValue();
        } else {
            return convertObjectTo(value, Short.class);
        }
    }

    /**
     * Get the result of an Object path expression as a float.
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public float getFloat(String path) {
        final Object value = get(path);
        //Groovy will always return a Double for floating point values.
        if(value instanceof Double) {
            return ((Double) value).floatValue();
        } else {
            return convertObjectTo(value, Float.class);
        }
    }

    /**
     * Get the result of an Object path expression as a double.
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public double getDouble(String path) {
        final Object value = get(path);
        if(value instanceof Double) {
            return (Double) value;
        }
        return convertObjectTo(value, Double.class);
    }

    /**
     * Get the result of an Object path expression as a long.
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public long getLong(String path) {
        //The type returned from Groovy depends on the input, so we need to handle different numerical types.
        Object value = get(path);
        if(value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Short) {
            return ((Short)value).longValue();
        } else if (value instanceof Integer) {
            return ((Integer)value).longValue();
        } else {
            return convertObjectTo(value, Long.class);
        }
    }

    /**
     * Get the result of an Object path expression as a string.
     *
     * @param path The Object path.
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public String getString(String path) {
        return convertObjectTo(get(path), String.class);
    }

    /**
     * Get the result of an Object path expression as a list.
     *
     * @param path The Object path.
     * @param <T> The list type
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <T> List<T> getList(String path) {
        return get(path);
    }

    /**
     * Get the result of an Object path expression as a list.
     *
     * @param path The Object path.
     * @param genericType The generic list type
     * @param <T> The type
     * @return The object matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <T> List<T> getList(String path, Class<T> genericType) {
        final List<T> original = get(path);
        final List<T> newList = new LinkedList<T>();
        for (T t : original) {
            newList.add(convertObjectTo(t, genericType));
        }
        return Collections.unmodifiableList(newList);
    }

    /**
     * Get the result of an Object path expression as a map.
     *
     * @param path The Object path.
     * @param <K> The type of the expected key
     * @param <V> The type of the expected value
     * @return The map matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <K,V> Map<K, V> getMap(String path) {
        return get(path);
    }

    /**
     * Get the result of an Object path expression as a map.
     *
     * @param path The Object path.
     * @param keyType The type of the expected key
     * @param valueType The type of the expected value
     * @param <K> The type of the expected key
     * @param <V> The type of the expected value
     * @return The map matching the Object path. A {@java.lang.ClassCastException} will be thrown if the object
     * cannot be casted to the expected type.
     */
    public <K,V> Map<K, V> getMap(String path, Class<K> keyType, Class<V> valueType) {
        final Map<K, V> originalMap = get(path);
        final Map<K, V> newMap = new HashMap<K, V>();
        for (Entry<K, V> entry : originalMap.entrySet()) {
            final K key = entry.getKey() == null ? null : convertObjectTo(entry.getKey(), keyType);
            final V value = entry.getValue() == null ? null : convertObjectTo(entry.getValue(), valueType);
            newMap.put(key, value);
        }
        return Collections.unmodifiableMap(newMap);
    }

    /**
     *  Get the result of a Object path expression as a java Object.
     * E.g. given the following Object document:
     * <pre>
     * { "store": {
     *   "book": [
     *    { "category": "reference",
     *      "author": "Nigel Rees",
     *      "title": "Sayings of the Century",
     *      "price": 8.95
     *    },
     *    { "category": "fiction",
     *      "author": "Evelyn Waugh",
     *      "title": "Sword of Honour",
     *      "price": 12.99
     *    },
     *    { "category": "fiction",
     *      "author": "Herman Melville",
     *      "title": "Moby Dick",
     *      "isbn": "0-553-21311-3",
     *      "price": 8.99
     *    },
     *    { "category": "fiction",
     *      "author": "J. R. R. Tolkien",
     *      "title": "The Lord of the Rings",
     *      "isbn": "0-395-19395-8",
     *      "price": 22.99
     *    }
     *  ],
     *    "bicycle": {
     *      "color": "red",
     *      "price": 19.95
     *    }
     *  }
     * }
     * </pre>
     * And a Java object like this:
     *
     * <pre>
     * public class Book {
     *      private String category;
     *      private String author;
     *      private String title;
     *      private String isbn;
     *      private float price;
     *
     *      public String getCategory() {
     *         return category;
     *      }
     *
     *     public void setCategory(String category) {
     *         this.category = category;
     *     }
     *
     *    public String getAuthor() {
     *          return author;
     *     }
     *
     *    public void setAuthor(String author) {
     *         this.author = author;
     *    }
     *
     *    public String getTitle() {
     *         return title;
     *    }
     *
     *    public void setTitle(String title) {
     *        this.title = title;
     *    }
     *
     *    public String getIsbn() {
     *             return isbn;
     *    }
     *
     *    public void setIsbn(String isbn) {
     *          this.isbn = isbn;
     *    }
     *
     *    public float getPrice() {
     *        return price;
     *    }
     *
     *    public void setPrice(float price) {
     *             this.price = price;
     *   }
     * }
     * </pre>
     *
     * Then
     * <pre>
     * Book book = from(Object).getObject("store.book[2]", Book.class);
     * </pre>
     *
     * maps the second book to a Book instance.
     *
     * @param path  The path to the object to map
     * @param objectType The class type of the expected object
     * @param <T> The type of the expected object
     * @return The object
     */
    public <T> T getObject(String path, Class<T> objectType) {
        Object object  = getJsonObject(path);
        if(object == null) {
            return null;
        } else if(object instanceof List || object instanceof  Map) {
            // TODO Avoid double parsing
            object = new JsonBuilder(object).toString();
        } else {
            return convertObjectTo(object, objectType);
        }

        final ObjectMapperType type;
        final ObjectMapperConfig config;
        if(objectMapperFactory == null) {
            type = null;
            config = objectMapperConfig();
        } else if(objectMapperFactory instanceof GsonObjectMapperFactory) {
            type = ObjectMapperType.GSON;
            config = objectMapperConfig().defaultObjectMapperType(type).gsonObjectMapperFactory((GsonObjectMapperFactory) objectMapperFactory);
        } else {
            type = ObjectMapperType.JACKSON_1;
            config = objectMapperConfig().defaultObjectMapperType(type).jackson1ObjectMapperFactory((Jackson1ObjectMapperFactory) objectMapperFactory);
        }


        final Object finalObject = object;
        ResponseBodyData d = new ResponseBodyData() {
            public String asString() {
                return (String) finalObject;
            }

            public byte[] asByteArray() {
                return new byte[0];
            }

            public InputStream asInputStream() {
                return null;
            }
        };

        return ObjectMapping.deserialize(d, objectType, "application/json","", null, type, config);
    }

    /**
     * Get the XML as a prettified string.
     *
     * @return The XML as a prettified String.
     */
    public String prettify() {
        return new Prettifier().prettify(JsonOutput.toJson(json), Parser.JSON);
    }

    /**
     * Get and print the XML as a prettified string.
     *
     * @return The XML as a prettified String.
     */
    public String prettyPrint() {
        final String pretty = prettify();
        System.out.println(pretty);
        return pretty;
    }

    /**
     * Configure JsonPath to use a specific Gson object mapper factory
     * @param factory The gson object mapper factory instance
     * @return a new JsonPath instance
     */
    public JsonPath using(GsonObjectMapperFactory factory) {
        return new JsonPath(this, factory);
    }

    /**
     * Configure JsonPath to use a specific Jackson object mapper factory
     * @param factory The Jackson object mapper factory instance
     * @return a new JsonPath instance
     */
    public JsonPath using(Jackson1ObjectMapperFactory factory) {
        return new JsonPath(this, factory);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param text The text containing the Object document
     */
    public static JsonPath given(String text) {
        return new JsonPath(text);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param stream The stream containing the Object document
     */
    public static JsonPath given(InputStream stream) {
        return new JsonPath(stream);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param file The file containing the Object document
     */
    public static JsonPath given(File file) {
        return new JsonPath(file);
    }
    /**
     * Instantiate a new JsonPath instance.
     *
     * @param reader The reader containing the Object document
     */
    public static JsonPath given(Reader reader) {
        return new JsonPath(reader);
    }
    /**
     * Instantiate a new JsonPath instance.
     *
     * @param url The URL containing the Object document
     */
    public static JsonPath given(URL url) {
        return new JsonPath(url);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param stream The stream containing the Object document
     */
    public static JsonPath with(InputStream stream) {
        return new JsonPath(stream);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param text The text containing the Object document
     */
    public static JsonPath with(String text) {
        return new JsonPath(text);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param file The file containing the Object document
     */
    public static JsonPath with(File file) {
        return new JsonPath(file);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param reader The reader containing the Object document
     */
    public static JsonPath with(Reader reader) {
        return new JsonPath(reader);
    }
    /**
     * Instantiate a new JsonPath instance.
     *
     * @param url The URI containing the Object document
     */
    public static JsonPath with(URL url) {
        return new JsonPath(url);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param stream The stream containing the Object document
     */
    public static JsonPath from(InputStream stream) {
        return new JsonPath(stream);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param text The text containing the Object document
     */
    public static JsonPath from(String text) {
        return new JsonPath(text);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param file The file containing the Object document
     */
    public static JsonPath from(File file) {
        return new JsonPath(file);
    }

    /**
     * Instantiate a new JsonPath instance.
     *
     * @param reader The reader containing the Object document
     */
    public static JsonPath from(Reader reader) {
        return new JsonPath(reader);
    }
    /**
     * Instantiate a new JsonPath instance.
     *
     * @param url The URI containing the Object document
     */
    public static JsonPath from(URL url) {
        return new JsonPath(url);
    }

    /**
     * Set the root path of the document so that you don't need to write the entire path. E.g.
     * <pre>
     * final JsonPath jsonPath = new JsonPath(Object).setRoot("store.book");
     * assertThat(jsonPath.getInt("size()"), equalTo(4));
     * assertThat(jsonPath.getList("author", String.class), hasItem("J. R. R. Tolkien"));
     * </pre>
     *
     * @param rootPath The root path to use.
     */
    public JsonPath setRoot(String rootPath) {
        notNull(rootPath, "Root path");
        this.rootPath = rootPath;
        return this;
    }

    private Object parseInputStream(final InputStream stream)  {
        return new ExceptionCatcher() {
            protected Object method(JsonSlurper slurper) throws Exception {
                return slurper.parse(toReader(stream));
            }
        }.invoke();
    }

    private Object parseReader(final Reader reader)  {
        return new ExceptionCatcher() {
            protected Object method(JsonSlurper slurper) throws Exception {
                return slurper.parse(reader);
            }
        }.invoke();
    }

    private Object parseFile(final File file)  {
        return new ExceptionCatcher() {
            protected Object method(JsonSlurper slurper) throws Exception {
                return slurper.parse(new FileReader(file));
            }
        }.invoke();
    }

    private Object parseURL(final URL url)  {
        return new ExceptionCatcher() {
            protected Object method(JsonSlurper slurper) throws Exception {
                return slurper.parse(toReader(url.openStream()));
            }
        }.invoke();
    }

    private BufferedReader toReader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in));
    }

    private abstract class ExceptionCatcher {

        protected abstract Object method(JsonSlurper slurper) throws Exception;

        public Object invoke() {
            try {
                return method(new JsonSlurper());
            } catch(Exception e) {
                throw new ParsePathException("Failed to parse the Object document", e);
            }
        }
    }

    public <T> T getJsonObject(String path) {
        final JSONAssertion jsonAssertion = createJsonAssertion(path);
        return (T) jsonAssertion.getAsJsonObject(json);
    }

    private JSONAssertion createJsonAssertion(String path) {
        notNull(path, "path");
        final JSONAssertion jsonAssertion = new JSONAssertion();
        final String root = rootPath.equals("") ? rootPath : rootPath.endsWith(".") ? rootPath : rootPath + ".";
        jsonAssertion.setKey(root + path);
        return jsonAssertion;
    }
}
