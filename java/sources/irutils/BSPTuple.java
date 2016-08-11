package irutils;

import java.util.List;

/**
 * BSPTuple.java
 *
 *
 * Created: Wed Jul 18 13:10:42 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: BSPTuple.java,v 1.6 2001/09/07 13:32:20 wrogers Exp $
 */

public class BSPTuple<E> {
  private String key;
  private E value;
  /**
   * instantiate new key/value pair object (tuple)
   * @param key   key for tuple.
   * @param value value for key
   */
  public BSPTuple (String key, E value)
  {
    this.key = key;
    this.value = value;
  }

  public String getKey()
  {
    return this.key;
  }

  public E getValue()
  {
    return this.value;
  }

  /**
   * Implementation of toString to override default implementation in
   * java.lang.List<String>.
   * @return string representation of tuple object.
   */  
  public String toString()
  {
    return "key: " + this.key + ", value: " + value;
  }
}// BSPTuple
