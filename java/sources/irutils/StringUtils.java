package irutils;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * StringUtils.java
 *
 *
 * Created: Tue Jul 10 09:05:44 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: StringUtils.java,v 1.6 2001/09/20 15:00:22 wrogers Exp $
 */

public final class StringUtils extends Object
{

  /**
   * Split string into substrings based on delimitor characters supplied;
   * resulting substrings are placed in array list in order of appearance.
   *
   * @param textstring string to be split.
   * @param delimitchars delimitor characters.
   * @return List containing substrings or empty list if no strings
   *         were split.
   */
  public static List split(String textstring, String delimitchars)
  {
    StringTokenizer st = new StringTokenizer(textstring, delimitchars, true);
    List list = new ArrayList(st.countTokens());
    String previousToken = "";
    while (st.hasMoreTokens()) 
      {
	String token = st.nextToken();
	if (token.indexOf(delimitchars) < 0 ) {
	  list.add(token);
	} else if (previousToken.indexOf(delimitchars) > -1) {
	  list.add("");
	} 
	previousToken = token;
      }
    if (previousToken.indexOf(delimitchars) > -1) {
      list.add("");
    } 
    return list;
  }

  /**
   * Split string into substrings based on delimitor characters supplied;
   * return substring specified by position.  If there are less tokens than
   * pos then return null.
   *
   * @param textstring string to be split.
   * @param delimitchars delimitor characters.  
   * @param pos position of substring to be returned.
   * @return substring at position pos or null if string has less tokens than pos.
   */

  public static String getToken(String textstring, String delimitchars, int pos)
  {
    int i = 0;
    StringTokenizer st = new StringTokenizer(textstring, delimitchars, true); 
    String previousToken = "";
    while (st.hasMoreTokens()) {
      String tok = (String)st.nextToken();

      if (tok.indexOf(delimitchars) < 0 ) {
	if (i == pos) {
	  return tok;
	}
	i++;
      } else if (previousToken.indexOf(delimitchars) > -1) {
	if (i == pos) {
	  return "";
	}
	i++;
      }
      previousToken = tok;
    }
    if (previousToken.indexOf(delimitchars) > -1 && i == pos) {
      return "";
    }
    return null;
  }

  /**
   * Write out string elements of arraylist as a brace encapsulated
   * list separated by spaces; if a token contains spaces then
   * encapsulate the token with braces.
   *
   * @param list  array list of strings to be joined
   * @return string joined strings of array list separated by spaces
   *         encapsulated by braces.
   */
  public static String list(List list)
  {
    StringBuffer sb = new StringBuffer();
    Iterator it = list.iterator();
    sb.append("{");
    while ( it.hasNext() )
      {
	String token = (String)it.next();
	if (token.length() == 0 || token.indexOf(" ") > -1 ) {
	  // if token contains spaces or is of length zero 
	  // then encapsulate token with braces.
	  sb.append("{");
	  sb.append(token);
	  sb.append("}");
	} else {
	  sb.append(token);
	}
	if (it.hasNext()) sb.append(" ");
      }
    sb.append("}");
    return sb.toString();
  }

  /**
   * Write out string elements of arraylist separated by joinstring specified
   * by user.
   * @param list       array list of strings to be joined
   * @param joinString character to join strings together
   * @return string containing joined strings of array list separated by
   *         joinstring.
   */
  public static String join(List list, String joinString)
  {
    StringBuffer sb = new StringBuffer();
    Iterator it = list.iterator();
    while ( it.hasNext() )
      {
	sb.append(it.next());
	if (it.hasNext()) sb.append(joinString);
      }
    return sb.toString();
  }

  public static void main(String[] args)
  {
    boolean debug = false;
    if (args.length > 1) {
      String separator = args[0];
      for (int i = 1; i < args.length; i++)
	{
	  List keyIndices = split(args[i], separator);
	  if (debug) {
	    System.out.println("string : " + args[i] + 
			       ", separator: " + separator +
			       " => " + list(keyIndices) );
	  } else  {
	    System.out.println(list(keyIndices));
	  }
	}
    } else {
      System.out.println("usage: irutils.StringUtils <separator> list [|list]");
    }
  }

} // StringUtils

