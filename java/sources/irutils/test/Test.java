package irutils;

import irutils.TemporaryPostingsPool;
import java.util.Iterator;

/**
 * Test.java
 *
 *
 * Created: Wed May 28 11:17:35 2003
 *
 * @author <a href="mailto:wrogers@nls10.nlm.nih.gov">Willie Rogers</a>
 * @version 0.01
 */

public class Test {
  public final static void main(String[] args)
  {
    int[] address = new int[20];
    TemporaryPostingsPool pool = new TemporaryPostingsPool();
    int link = pool.add("postings0", -1);
    for (int i = 1; i < 20; i++) {
      link = pool.add("postings" + i, link);
    }
    Iterator iter = pool.get(link).iterator();
    while (iter.hasNext()) {
      System.out.println("+ " + iter.next());
    }
  }  
}// Test
