package gov.nih.nlm.nls.util.trie;

import java.io.Serializable;

public class Node <T> implements Serializable
{
  protected char c;
  
  protected T value = null;
	
  protected Node <T> child = null;
  protected Node <T> sibling = null;
}
