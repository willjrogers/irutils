package utils;

/**
 * BSPIndexCreateException.java
 *
 *
 * Created: Fri Jul 20 10:51:39 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: BSPIndexCreateException.java,v 1.2 2001/07/25 18:45:39 wrogers Exp $
 */

public class BSPIndexCreateException extends Exception {
  public BSPIndexCreateException () {
    super("BSPIndexCreateException");
  }
  public BSPIndexCreateException (String message) {
    super(message);
  }
}// BSPIndexCreateException
