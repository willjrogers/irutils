package gov.nih.nlm.nls.util.trie;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TrieTestWSD
{
  private static Pattern p = Pattern.compile(" ");

  private static Trie <int []> getSerializedTrie(String file)
  {
	Trie <int []> trie = null;

	try
	{
	  ObjectInputStream o = new ObjectInputStream(new GZIPInputStream(new FileInputStream(file + ".ser.gz")));
	  trie = (Trie <int []>)o.readObject();
	  o.close();
	}
	catch (Exception e)
	{ }

	return trie;
  }

  public static Trie <int []> getTrie(String file) throws IOException
  {
	Trie <int []> trie = getSerializedTrie(file);

	// If no serialization
	if (trie == null)
	{
	  trie = new Trie <int []> ();
		
	  System.out.println("Loading...");

	  BufferedReader b = new BufferedReader(new FileReader(file));

	  String line;

	  int i = 0;

	  while ((line = b.readLine()) != null)
	  {
	    String [] tokens = p.split(line);

  	    int [] vector = new int [129];

	    for (int j=1; j < tokens.length; j++)
		{ vector[j-1] = Integer.parseInt(tokens[j]); }
			
		trie.insert(tokens[0], vector); 

		if (i % 10000 == 0) System.out.println(i);
		  
		i++;
	  }

	  b.close();
	  
	  // Store a serialized version of the trie structure
	  ObjectOutputStream o = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(file + ".ser.gz")));
	  o.writeObject(trie);
	  o.close();
	}

	return trie;
  }

  public static void main (String [] argc) throws IOException
  {
	Trie <int []> t = getTrie(argc[0]);
	
    System.out.println("Loaded all the terms!");

    //t.traverse();

    for (int value : t.get("A1059"))
    { System.out.println(value); }
    
/*    for (int j = 0; j < 1000000; j++)
    {
      t.get("nbsp");
    }*/
  }
}