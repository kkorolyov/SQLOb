package dev.kkorolyov.sqlob.persistence;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import dev.kkorolyov.sqlob.annotation.Column;

/**
 * Manages persistence at the field/column level.
 */
final class SqlobField {
	final String name;
	final String type;
	final int typeCode;
	private final Field field;
	private final Extractor extractor;
	private final SqlobClass<?> reference;
	
	SqlobField(Field field, Extractor extractor, String type, SqlobClass<?> reference) {
		this.field = field;
		this.extractor = extractor;
		this.type = type;
		this.reference = reference;
		
		Column override = this.field.getAnnotation(Column.class);
		name = (override == null || override.value().length() <= 0) ? this.field.getName() : override.value();
		
		typeCode = JDBCType.valueOf(type.split("[\\s(]")[0]).getVendorTypeNumber();
		
		this.field.setAccessible(true);
	}

	/** @return value of this field on {@code obj} */
	Object get(Object obj) {
		try {
			return field.get(obj);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("This should never happen");
		}
	}

	/** @return type of the encapsulated field */
	Class<?> getType() {
		return field.getType();
	}

	UUID put(Object obj, Connection conn) throws SQLException {
		return reference.put(obj, conn);
	}
	
	String getInit(String idName) {
		String init = name + " " + type;
		
		if (isReference())
			init += ", FOREIGN KEY (" + name + ") REFERENCES " + reference.name + "(" + idName + ")";
		
		return init;
	}
	
	Object transform(Object o, Connection conn) throws SQLException {	// Transforms to UUID String if reference
		if (isReference()) {
			UUID id = reference.getId(o, conn);
			return id == null ? null : id.toString();
		}
		return o;
	}
	
	void apply(Object instance, ResultSet rs, Connection conn) throws SQLException {
		Object value = (extractor == null ? rs.getObject(name) : extractor.execute(rs, name));
		
		if (isReference() && value != null)
			value = reference.get(UUID.fromString((String) value), conn);
		
		try {
			field.set(instance, value);
		} catch (IllegalAccessException e) {
			throw new NonPersistableException(field + " is inaccessible", e);
		}
	}
	
	boolean isReference() {
		return reference != null;
	}
}
