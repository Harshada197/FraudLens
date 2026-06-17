package com.fraudlens.data;

import com.fraudlens.model.Account;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory Pattern: creates the 100 seeded accounts.
 *
 * SOLID-S: single responsibility — account creation only.
 *
 * Account ranges:
 *   001–016  Cycle-pattern accounts
 *   017–040  Regular users (some involved in rapid-hop chains)
 *   041–044  Threshold/structuring senders
 *   045–052  Hub pattern accounts
 *   053–080  Clean regular users
 *   081–100  Merchant / utility accounts (receive-only)
 */
public class AccountFactory {

    private static final String[] NAMES = {
        // 001–010
        "Aarav Sharma",    "Priya Patel",     "Rohan Mehta",     "Sneha Gupta",     "Vikram Singh",
        "Ananya Reddy",    "Karan Joshi",     "Meera Iyer",      "Arjun Nair",      "Divya Kapoor",
        // 011–020
        "Rahul Verma",     "Pooja Desai",     "Aditya Rao",      "Neha Kulkarni",   "Siddharth Das",
        "Kavya Menon",     "Manish Tiwari",   "Ritu Agarwal",    "Amit Saxena",     "Shreya Pillai",
        // 021–030
        "Suresh Kumar",    "Lakshmi Bhat",    "Nikhil Pandey",   "Swati Mishra",    "Rajesh Thakur",
        "Deepa Naidu",     "Varun Choudhary", "Preeti Jain",     "Harish Hegde",    "Komal Shah",
        // 031–040
        "Ganesh Patil",    "Sunita Rathore",  "Ashok Shetty",    "Nandini Rao",     "Manoj Dubey",
        "Usha Prasad",     "Sanjay Bhatt",    "Rekha Mahajan",   "Vivek Chauhan",   "Anita Bose",
        // 041–050
        "Prakash Goel",    "Sarita Mathur",   "Dinesh Malhotra", "Jyoti Srivastava","Ramesh Khatri",
        "Padma Nambiar",   "Girish Tandon",   "Kavita Khanna",   "Sunil Wagh",      "Bhavna Sethi",
        // 051–060
        "Ajay Deshpande",  "Pallavi Mohan",   "Tushar Banerjee", "Rashmi Kaul",     "Vinod Sinha",
        "Archana Dixit",   "Pankaj Bajaj",    "Smita Gokhale",   "Gaurav Lal",      "Madhuri Vyas",
        // 061–070
        "Santosh Rawat",   "Rina Chopra",     "Nilesh Datta",    "Shilpa Purohit",  "Yash Grover",
        "Manju Rawal",     "Deepak Bhandari", "Sapna Dhawan",    "Tarun Khurana",   "Leela Narayan",
        // 071–080
        "Kishore Pandya",  "Suman Ahuja",     "Rajiv Mehra",     "Geeta Garg",      "Pravin Karnik",
        "Kamala Devi",     "Hemant Soni",     "Nisha Oberoi",    "Mukesh Arora",    "Asha Trivedi",
        // 081–090  (Merchants / Utilities)
        "PhonePe Merchant","Amazon Pay Seller","Flipkart Store",  "BigBasket Order", "Swiggy Delivery",
        "Zomato Payment",  "Uber Rides",      "Ola Cabs",        "BSNL Recharge",   "Airtel Payments",
        // 091–100  (Merchants / Utilities)
        "Jio Recharge",    "BESCOM Electric", "BWSSB Water",     "LIC Premium",     "MutualFund SIP",
        "Netflix India",   "Hotstar Sub",     "Gym Membership",  "Apartment Maint", "School Fees"
    };

    public List<Account> createAccounts() {
        List<Account> accounts = new ArrayList<>(100);
        for (int i = 1; i <= 100; i++) {
            String id   = "ACC_" + String.format("%03d", i);
            String name = (i <= NAMES.length) ? NAMES[i - 1] : "User " + id;
            accounts.add(new Account(id, name));
        }
        return accounts;
    }
}
