import java.util.ArrayList;
import java.text.DecimalFormat;
import java.io.*;

/** 
* @module Change.java
* @version 1.2
* @since 2013-03-19
* @author Jimmy Lin (u5223173) - u5223173@uds.anu.edu.au
*
* Two modes:
*    Test Mode: 
*       To generate testcases.txt, please type in console: 
*           java Change -t 
*
*    General Mode:
*       java Change arg  
*
* Edited by MacVim
* Class Info auto-generated by Snippet 
*/
public class Change {
    static final String OUTPUT_FORMAT = "%30s %7s     ";
    int amount; 
    int remain;
    ArrayList<Coin> coinSet;

    public Change(int amount){
//{{{
        this.amount = amount;
        this.remain = amount;
        this.coinSet = new ArrayList<Coin> ();
        
        /** add to the coinSet */
        this.coinSet.add(new Coin(200));
        this.coinSet.add(new Coin(100));
        this.coinSet.add(new Coin(50));
        this.coinSet.add(new Coin(20));
        this.coinSet.add(new Coin(10));
        this.coinSet.add(new Coin(5));
//}}}
    }

    public int getCoinNumber(Coin coin) {
//{{{
        int num;
        if (this.remain < 0) return 0;

        int cents;
        cents = coin.cents;
        num = this.remain / cents;
//}}}
        return num;
    }

    public void amountCastUp() {
        if (this.amount % 5 == 3)
            this.remain += 2;
        if (this.amount % 5 == 4)
            this.remain += 1;
    }

    public String toString(){
//{{{
        String str = "";
        boolean firstCoin = true;
        if (this.amount / 100 < 1) {
            str += this.amount + "c";
        } else {
            double temp;
            temp = this.amount / 100.0;
            DecimalFormat df = new DecimalFormat ("###.00");
            str += df.format(temp);
        }

        Coin temp;
        int n = this.coinSet.size();
        for (int i = 0 ; i < n ; i++ ) {
            temp = this.coinSet.get(i);
            if (temp.number > 0) {
                if (firstCoin) {
                    str += " = " + temp.number + " x " + temp.toString();
                    firstCoin = false;
                } else {
                    str += " + " + temp.number + " x " + temp.toString();
                }
            }
        }
//}}}
        return str;
    }

    public String toString_test(){
//{{{
        String str = "";
        str = String.format( OUTPUT_FORMAT, "break in coin size sequence", this.amount) + this.toString();
//}}}
        return str;
    }

    public static void main(String [] args) throws IOException {
//{{{
        if (args.length == 0) return ;
        /** Parse the input */
        int amount = Integer.parseInt(args[0]);

        /** Invalid input (beyond the scope) */
        if (amount >= 498 || amount <= 2) {
            System.out.println("argument "+ args[0] +" is too large");
            return ;
        }

        /** Initialization for new Change object */
        Change ch = new Change(amount);
        ch.amountCastUp(); // make casting for the final digit: 3,4,8,9

        /** Get the number of each type of coin */
        for (int i = 0; i < ch.coinSet.size(); i ++) {
            Coin temp = ch.coinSet.get(i);
            temp.number = ch.getCoinNumber(temp);
            ch.remain -= temp.cents * temp.number;
        }
        
        /** Output to console */
        System.out.println(ch.toString());
//}}}
        return ;
    }
}

/** Coin structure to store information about various coin */
class Coin {
//{{{
    int cents;
    int number;

    public Coin (int cents) {
        this.cents = cents;
    }

    public String toString(){
        if (this.cents / 100 < 1) {
            return this.cents + "c";
        } else {
            return "$"+ (this.cents/100);
        }
    }
//}}}
}