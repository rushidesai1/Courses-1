package gui;

import java.util.Observable;
import java.util.Scanner;

import scanner.*;
import types.*;
import visitors.*;

import java.io.*;

import org.xml.sax.SAXException;

import scanner.SvgScanner;
import visitors.Java2DRenderer;
import visitors.Validator;
import elements.SvgContainerElement;

/**
 * Entry point to the underlying application logic: the scanner,
 * parser and parse tree and the visitor pattern mechanism for
 * extracting information from the parse tree.
 * This creates SAX parser,
 * opens an SVG document, reads it in and builds
 * a parse tree, then creates various visitor objects
 * to validate and render the original document.
 * The View and COntroller manage response to controls to drive re-rendering.
 * 
 * @author Ian Barnes original OOPS version
 * @author Chris Johnson modified OOPS, and all Gaga
 * @version $Revision: 1532 $, $Date: 2011-05-04 11:51:55 +1000 (Wed, 04 May 2011) $
 */

/** The main application class. Creates scanner, the tree root (and its only
 * child -- the document node), validator and renderer visitors.
 * Fatal exits if 
 *   -  if the document file cannot be read
 *   -  if the svg document cannot be parsed (malformed)
 */
public class Model extends Observable {

    private String docName;
    public String getDocName(){ return docName;}
    
    /* create as stub object for attaching to View */
    public Model(){}
    
    SvgContainerElement root; // the root to the tree object
    SvgContainerElement svg; // the one and only child of the root
    
    /* handle the input document parsing and tree building */ 
    /*
     * TODO add actions to notify observers when a new document is opened
     * TODO recover and continue after detecting and reporting an error or warning
     */
    public void open(String []args) {

        System.out.println("This is the Gaga system " +
                "-- your friendly SVG renderer.");

        SvgScanner scanner; // SAX scanner which also builds the tree
   
        Validator validator;
        
        String sourceFilename = pickupFilename( args);
           
        try {
            scanner = new SvgScanner(sourceFilename);
            root = scanner.getRoot();

            if (root.children == null && root.children.size() != 1) {
                System.err.println("The document is empty, nothing to do...");
                System.exit(0);
            } else {

                svg = (SvgContainerElement) root.children.get(0);
                //svg.prettyPrint(0, System.out); // debug - original tree
                validator = new Validator();
                svg.accept(validator);
                //svg.prettyPrint(0, System.out); //debug - after validator traversal

            }

        } catch (SAXException e) {
            System.err.println("Cannot parse the document " + sourceFilename);
            System.exit(1);
        } catch (IOException e) {
            
        	System.err.println("Cannot open the document " + sourceFilename );
        	e.printStackTrace();
            System.exit(1);
        }
    	
    }

	/**
	 * @param args command line arguments
	 * @return source filename from the first argument or by prompt from input if there is no argument
	 */
	private String pickupFilename(String... args) {
		String sourceFilename = "";
		if (args.length == 0) {
			// no arg, prompt for console input
        	System.out.print("enter SVG filename: ");
        	Scanner in = new Scanner(System.in);
        	sourceFilename = in.next();
        	}
		
        else if (args.length == 1) {
        	sourceFilename = args[0];
        	}
      
        else  {
            System.err.println("Usage: java Forge [file.svg]");
            System.exit(1);
        }
		
        if (! sourceFilename.endsWith((".svg"))) {
        	System.err.println("filename " + sourceFilename + " requires .svg extension");
            System.exit(1);
        }
        docName = sourceFilename;
		return sourceFilename;
	}
	
	/**
     * Hook for launching tree traversal by visitors.
     */
	public void accept(Visitor v){
		svg.accept(v);
		}

}
