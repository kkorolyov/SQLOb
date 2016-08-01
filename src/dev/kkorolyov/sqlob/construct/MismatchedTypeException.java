package dev.kkorolyov.sqlob.construct;

/**
 * Exception thrown when an input type does not match a predetermined constraint.
 */
public class MismatchedTypeException extends Exception {
	private static final long serialVersionUID = 5247178864977750536L;

	/**
	 * Constructs an instance of this exception.
	 * @param expected expected type
	 * @param actual actual input type
	 */
	public MismatchedTypeException(Class<? extends Object> expected, Class<? extends Object> actual) {
		super("Expected: " + expected.getName() + ", Actual: " + actual.getName());
	}
}