package dev.kkorolyov.sqlob.service

import dev.kkorolyov.simplelogs.Logger
import dev.kkorolyov.sqlob.annotation.Column
import dev.kkorolyov.sqlob.annotation.Table
import dev.kkorolyov.sqlob.annotation.Transient
import dev.kkorolyov.sqlob.persistence.NonPersistableException
import dev.kkorolyov.sqlob.utility.Converter
import dev.kkorolyov.sqlob.utility.Extractor
import groovy.transform.PackageScope
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Field

class MapperSpec extends Specification {
	static {
		Logger.getLogger("dev.kkorolyov.sqlob", Logger.Level.DEBUG, new PrintWriter(System.err));	// Enable logging
	}

  @Shared String stubSqlType = Constants.sanitize("SomeSQL")
	@Shared Converter stubConverter = { o -> o }
	@Shared Extractor stubExtractor = { rs, column -> rs.getString(column) }

  Mapper mapper = new Mapper()

	def "returns typemapped sqlType for class"() {
		Class c = Empty
		String sqlType = stubSqlType

		when:
		mapper.put(c, sqlType, stubExtractor)

		then:
		mapper.sql(c) == sqlType
	}
	def "returns typemapped sqlType for field"() {
		Class c = Empty
		String sqlType = stubSqlType

		when:
		mapper.put(c, sqlType, stubExtractor)

		then:
		mapper.sql(f) == sqlType

		where:
		f << [Multi.getDeclaredField("e1"),
					Multi.getDeclaredField("e2"),
					Multi.getDeclaredField("e3")]
	}
	def "sqlType is sanitized"() {
		Class<?> c = Empty
		String sqlType = "Bad SQ;L"

		when:
		mapper.put(c, sqlType, stubConverter, stubExtractor)

		then:
		mapper.sql(c) != sqlType
		mapper.sql(c) == Constants.sanitize(sqlType)
	}

	def "converts using typemapped converter"() {
		Class c = Empty
		Converter converter = { o -> "HI" }
		Object o = c.newInstance()

		when:
		mapper.put(c, stubSqlType, converter, stubExtractor)

		then:
		mapper.convert(o) != o
		mapper.convert(o) == converter.execute(o)
	}
	def "no conversion if no typemapped converter"() {
		Class c = Empty
		Object o = c.newInstance()

		expect:
		mapper.convert(o) == o
	}

	def "extracts using typemapped extractor"() {
		// TODO
	}

	def "getPersistableFields() returns one of each persistable field"() {
    expect:
    Iterable<Field> results = mapper.getPersistableFields(c)

    results.containsAll(fields)
    results.size() == size

    where:
    c << [Multi]
    fields << [[Multi.getDeclaredField("e1"), Multi.getDeclaredField("e2"), Multi.getDeclaredField("e3")]]
    size << [3]
  }

  def "getPersistableFields() ignores Transient-tagged fields"() {
    expect:
    mapper.getPersistableFields(c).size() == size

    where:
    c << [TransientTag, TransientTagPlusOne]
    size << [0, 1]
  }
  def "getPersistableFields() ignores transient fields"() {
    expect:
    mapper.getPersistableFields(c).size() == size

    where:
    c << [TransientModifier, TransientModifierPlusOne]
    size << [0, 1]
  }
  def  "getPersistedFields() ignores static fields"() {
    expect:
    mapper.getPersistableFields(c).size() == size

    where:
    c << [StaticModifier, StaticModifierPlusOne]
    size << [0, 1]
  }

  def "getAssociatedClasses() returns one of each class"() {
    expect:
    Iterable<Class<?>> results = mapper.getAssociatedClasses(c)

    results.containsAll(classes)
    results.size() == size

    where:
    c << [Multi, SelfRef, RefLoop1, RefLoop2]
    classes << [[Multi, Empty],
                SelfRef,
                [RefLoop1, RefLoop2],
                [RefLoop1, RefLoop2]]
    size << [2, 1, 2, 2]
  }

	def "getName(Class) returns simple name of non-Table-tagged class"() {
		expect:
		mapper.getName(c) == name

		where:
		c << [NonTagged]
		name << ["NonTagged"]
	}
	def "getName(Class) returns custom name of Table-tagged class"() {
		expect:
		mapper.getName(c) == name

		where:
		c << [Tagged]
		name << ["CustomTable"]
	}
	def "getName(Class) excepts on empty Table tag"() {
		when:
		mapper.getName(EmptyTagged)

		then:
		thrown NonPersistableException
	}

	def "getName(Field) returns name of non-Column-tagged field"() {
		expect:
		mapper.getName(f) == name

		where:
		f << [NonTagged.getDeclaredField("s")]
		name << ["s"]
	}
	def "getName(Field) returns custom name of Column-tagged field"() {
		expect:
		mapper.getName(f) == name

		where:
		f << [Tagged.getDeclaredField("s")]
		name << ["CustomColumn"]
	}
	def "getName(Field) excepts on empty Column tag"() {
		when:
		mapper.getName(EmptyTagged.getDeclaredField("s"))

		then:
		thrown NonPersistableException
	}

  def "typemapped classes are primitive"() {
    Class<?> c = Empty

    when:
    mapper.put(c, stubSqlType, stubExtractor)

    then:
    mapper.isPrimitive(c)
    !mapper.isComplex(c)
  }
  def "non-typemapped classes are complex"() {
    Class<?> c = Empty

    expect:
    mapper.isComplex(c)
    !mapper.isPrimitive(c)
  }

  def "fields of typemapped class are primitive"() {
    when:
    mapper.put(Empty, stubSqlType, stubExtractor)

    then:
    mapper.isPrimitive(f)
    !mapper.isComplex(f)

    where:
    f << [Multi.getDeclaredField("e1"), Multi.getDeclaredField("e2"), Multi.getDeclaredField("e3")]
  }
  def "fields of non-typemapped class are complex"() {
    expect:
    mapper.isComplex(f)
    !mapper.isPrimitive(f)

    where:
    f << [Multi.getDeclaredField("e1"), Multi.getDeclaredField("e2"), Multi.getDeclaredField("e3")]
  }

  class Empty {}

  class TransientTag {
    @Transient
    private Empty e
  }
  class TransientTagPlusOne {
    @Transient
    private Empty e1
    private Empty e2
  }

  class TransientModifier {
    private transient Empty e
  }
  class TransientModifierPlusOne {
    private transient Empty e1
    private Empty e2
  }

  class StaticModifier {
    private static Empty e
  }
  class StaticModifierPlusOne {
    private static Empty e1
    private Empty e2
  }

  class Multi {
    Empty e1
    @PackageScope Empty e2
    private Empty e3
  }

  class SelfRef {
    SelfRef selfRef1;
    @PackageScope SelfRef selfRef2
    private SelfRef selfRef2
  }

  class RefLoop1 {
    RefLoop2 ref
  }
  class RefLoop2 {
    RefLoop1 ref
  }

	class NonTagged {
		String s
	}
	@Table("CustomTable") class Tagged {
		@Column("CustomColumn") String s
	}
	@Table("") class EmptyTagged {
		@Column("") String s
	}
}
