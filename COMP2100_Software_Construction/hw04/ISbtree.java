/** 
* @module ISbtree.java
* @version 1.0 
* @since 2013-03-13
* @author Jimmy Lin (u5223173) - u5223173@uds.anu.edu.au
* 
* Edited by MacVim
* Class Info auto-generated by Snippet 
*/
class ISbtree implements IntSet{
    private Integer value;
    private int count = 0;
    private ISbtree left;
    private ISbtree right;

    // Constructor
    ISbtree (int n) {
        this.value = new Integer(n);
        this.count += 1;
        this.left = null;
        this.right = null;
    }

    //@Override
    public void insert (int n) { // insert n into the set
        if (this.value == null) {
            this.value = new Integer (n);
            this.count += 1;
            return ; // exit of this method
        } 
        if (this.isMember(n)){
            return ;
        } else {
            this.count += 1;
            if (this.value >= n) {
                /** insert ot left */
                if (this.left == null) {
                    this.left = new ISbtree (n);
                } else {
                    this.left.insert(n);
                }
            } else if (this.value < n) {
                /** insert to right */
                if (this.right == null){
                    this.right = new ISbtree (n);
                } else {
                    this.right.insert(n);
                }
            }
        }
    }
    //@Override
	public boolean isMember (int c) { // whether c is in the set
        if (this.value == null) {
            /** nothing to find further */
            return false;
        } else if (this.value > c) {
            /** search for the left branch */
            if (this.left == null) {
                return false;
            } else {
                return this.left.isMember(c);
            }
        } else if (this.value < c) {
            /** search for the right branch */
            if (this.right == null) {
                return false;
            } else {
                return this.right.isMember(c);
            }
        } else if (this.value == c) {
            /** find the Integer with the same value */
            return true;         
        }
        return false;
    }

    //@Override
    public void makeEmpty() {
        this.value = null;
        this.count = 0;
        this.left = null;
        this.right = null;
    }

    //@Override
	public int cardinality() {
        return count;
    }

}
