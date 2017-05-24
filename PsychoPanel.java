/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.co.pentest.psychoPATH;

import burp.IHttpRequestResponse;
import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BoxLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;

import javax.swing.JLabel;
import javax.swing.JList;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 *
 * @author ewilded
 */
public final class PsychoPanel extends JPanel {
    static String PROMPT_TITLE = "psychoPATH extension";

    protected JLabel docrootsLabel;        
    protected JButton docrootsPasteButton;
    protected JButton docrootsLoadButton;
    protected JButton docrootsRemoveButton;
    protected JButton docrootsClearButton;
    protected JButton docrootsAddButton;
    private JComboBox docrootsAddLists;     
    protected JList docrootsList; 
    protected JScrollPane docrootsListScroller;
   
    protected JLabel suffixesLabel;        
    protected JButton suffixesPasteButton;
    protected JButton suffixesLoadButton;
    protected JButton suffixesRemoveButton;
    protected JButton suffixesClearButton;
    protected JButton suffixesAddButton; 
    protected JList suffixesList; 
    protected JScrollPane suffixesListScroller;
 
    protected JLabel targetsLabel;        
    protected JButton targetsPasteButton;
    protected JButton targetsLoadButton;
    protected JButton targetsRemoveButton;
    protected JButton targetsClearButton;
    protected JButton targetsAddButton;    
    protected JList targetsList;
    protected JScrollPane targetsListScroller;
    protected JLabel fileName;
    protected JTextArea fileNameField;
    protected JTextArea logOutput;
    protected JList evasiveList;
    protected JList drivesList;
    protected JList breakupList;
    
    protected JComboBox windowsDrivesToUse;
    protected JCheckBox LFIOptimizeCheckBox;
    protected JCheckBox optimizeDocrootTraversalsBox;
    protected JCheckBox useAbsoluteWebrootsBox;
    protected String proto="https";
    protected String hostname="example.org";
    protected ArrayList<String> genericSuffixes;
    protected int defaultMaxTraversalsPerPayload=8; 
    protected int maxTraversalsPerPayload=8; // the number of maximum traversals in our payloads - used to pick the longest ones while optimizing - later we'll just calculate this value on the fly
    protected boolean optimizeDocroots=true; // whether to only prepend docroots with the longest versions of traversals
    protected boolean evasiveTechniques=true;
    protected boolean optimizeLFI=true; // whether to only prepend the filename with the longest versions of traversals when in LFI mode
    protected boolean LFImode=false; // whether we are in the LFI hunting mode - whether or not to use webroots at all
    protected boolean useAbsoluteWebroots=true; 
    protected ArrayList<String> basicTraversals;
    protected String[] defaultBreakups = {"20"}; // by default the only {BREAK} string is a white space (20 asciihex)
    protected String[] nonRecurrentEvasiveTraversals =  {"....//", "...//", ".....///"};  
    protected String[] breakupHolderTraversals = {"..{BREAK}/","{BREAK}../",".{BREAK}./","../{BREAK}"};     // currently the only break-up char is space; we'll make this customizable as well
    // other, yet not supported variants (might be added later)
    // break up holders mixed with non-recurrent filters
    // non-recurrent filter cases with mixed win-nix slashes
    // non-recurrent filter cases with mixed win-nix slashes mixed with break-up character
    private JComboBox slashesToUseCombo; 
    protected String slashesToUse="nix"; // possible values: nix,win,all
    protected String basicTraversal="../";
    

    PrintWriter stdout;
    
    public void updateScope(String proto, String hostname)
    {
        this.proto=proto;
        this.hostname=hostname;
        // ok, now we update the targets
 
        ArrayList<String> newList = new ArrayList<String>();
        newList.add(hostname);
        // if hostname is not an IP address - but contains dots, it is a domain name
        String ipPattern = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
               
        Pattern pattern = Pattern.compile(ipPattern);
        Matcher matcher = pattern.matcher(hostname);
        if(!matcher.matches())
        {
            // in such case split the string by dots, remove the tld
            // and propagate the list
            String[] parts = hostname.split(".");
            for(int i=0;i<parts.length-1;i++)
            {
                newList.add(parts[i]);
            }
        }
        // propagate the list to the interface
        targetsList.setListData(newList.toArray());       
        // now is the time to update suffixes
        genericSuffixes.clear();
        IHttpRequestResponse[] items = PsychoPATH.callbacks.getSiteMap(proto+"://"+hostname);
        for(int i=0;i<items.length;i++)
        {
            //if(items[i].getStatusCode()!=404) continue; // skipping 404s might not be a good idea - some servers return 404s for existing directories when no deeper existing URI is requested            
            String path=items[i].getUrl().getPath();
            path=path.substring(0,path.lastIndexOf("/"));
            if(!genericSuffixes.contains(path)) 
            {                
                genericSuffixes.add(path);
                logOutput("Adding "+path+" to the dir check payloads and attack suffixes.\n");
            }
        }        
        // we propagate the list to the GUI, so it can be adjusted before the attack
        String[] arr;
        arr = new String[genericSuffixes.size()];
        for(int i =0;i<genericSuffixes.size();i++)
        {
            arr[i]=genericSuffixes.get(i);
        }        
        appendListData(suffixesList,arr); // we cannot cast, we'll just merge              
        // the genericSuffixes list will, in turn, be used by the directory checker logic of the IntruderPayloadGenerator
        if(hostname!="example.org") this.logOutput("Scope propagated.\n");
    }
    protected void appendListData(JList list, String[] items)
    {
        ArrayList tmp = new ArrayList();

         for (int i=0; i < list.getModel().getSize(); i++) {
            String elem = (String) list.getModel().getElementAt(i);           
            tmp.add(elem);
        }
        for(String item: items)
        {
            if(!tmp.contains(item)) tmp.add(item);
        }
        list.setListData(tmp.toArray());
    }
    private void removeFromListData(JList list, String item)
    {
        ArrayList tmp = new ArrayList();

         for (int i=0; i < list.getModel().getSize(); i++) {
            String elem = (String) list.getModel().getElementAt(i); 
            if(!elem.equals(item)) tmp.add(elem);
        }
        list.setListData(tmp.toArray());
    }
    private void initiateEvasiveList()
    {
        appendListData(evasiveList,this.nonRecurrentEvasiveTraversals);
        String n[]={this.breakupHolderTraversals[0]}; 
        appendListData(evasiveList,n);
        this.evasiveTechniques=true;
    }
    private void disableEvasiveTechniques()
    {
        String [] empty = {};
        evasiveTechniques=false;
        evasiveList.setListData(empty); //for some weird reason removeAll did not want to work. 
        this.evasiveTechniques=false;
    }
    private void clearBreakups()
    {
        String [] empty={};
        breakupList.setListData(empty); // if breakup-list is empty, break-up payloads are ignored
        // additionally, we could search the list and remove elements with the {BREAK} holder
    }
    private void initiateBreakups()
    {
        appendListData(breakupList,this.defaultBreakups);
    }
    private void clearDrives()
    {
        String [] empty={};
        drivesList.setListData(empty);
    }
    PsychoPanel() {
        stdout = new PrintWriter(PsychoPATH.callbacks.getStdout(), true);
        genericSuffixes=new ArrayList<>();
        setLayout(new BorderLayout());
        // CREATE THE PANEL SKELETON
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel,BoxLayout.Y_AXIS));
        mainPanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        
        //JPanel optionsPanel = new JPanel();
        //optionsPanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        
        JPanel targetsPanel = new JPanel();
        
        JPanel targetsPanelRight = new JPanel();
        JPanel targetsPanelLeft = new JPanel();
        targetsPanelRight.setLayout(new BoxLayout(targetsPanelRight , BoxLayout.Y_AXIS));
        targetsPanelLeft.setLayout(new BoxLayout(targetsPanelLeft , BoxLayout.Y_AXIS));
        JPanel pathsPanel = new JPanel(new GridLayout(1,4));
        //JPanel logPanel = new JPanel();
        //logPanel.setLayout(new BoxLayout(logPanel, BoxLayout.Y_AXIS));        
    
        
        JPanel suffixesPanel = new JPanel(new GridLayout(1,2));
        JPanel suffixesPanelRight = new JPanel();
        JPanel suffixesPanelLeft = new JPanel();
        suffixesPanelRight.setLayout(new BoxLayout(suffixesPanelRight , BoxLayout.Y_AXIS));
        suffixesPanelLeft.setLayout(new BoxLayout(suffixesPanelLeft , BoxLayout.Y_AXIS));
        

        
        
        JPanel traversalPanel = new JPanel(new GridLayout(1,2));        
        JPanel traversalPanelLeft = new JPanel();
        traversalPanelLeft.setLayout(new BoxLayout(traversalPanelLeft, BoxLayout.Y_AXIS));
                
        JPanel traversalPanelRight = new JPanel();
        traversalPanelRight.setLayout(new BoxLayout(traversalPanelRight, BoxLayout.Y_AXIS));
                        
        
        JPanel docrootsPanel = new JPanel(new GridLayout(1,2));
        JPanel docrootsPanelRight = new JPanel();
        docrootsPanelRight.setLayout(new BoxLayout(docrootsPanelRight, BoxLayout.Y_AXIS));
        JPanel docrootsPanelLeft = new JPanel();
        docrootsPanelLeft.setLayout(new BoxLayout(docrootsPanelLeft, BoxLayout.Y_AXIS));        

        // PATHS PANEL START   
        // basic traversal units (please refer to the readme for an explanation)
        this.basicTraversals = new ArrayList<>();

        // the basic traversals list is not manageable
        this.basicTraversals.add(this.basicTraversal);                                  // simple '../'

        // SLASH-SETUP SECTION START
        
        JLabel slashesToUseLabel = new JLabel("Directory separator to use");
        slashesToUseCombo = new JComboBox();
        String[] slashNames= { "Nix / (default)", "Windows \\", "All"};
        for (String docrootListName : slashNames) {
            slashesToUseCombo.addItem(docrootListName);
        }
        slashesToUseCombo.addActionListener((ActionEvent e) -> {
            switch(slashesToUseCombo.getSelectedIndex())
            { 
                case 0 : { this.slashesToUse="nix"; break;}
                case 1 : { this.slashesToUse="win"; break;}
                case 2 : { this.slashesToUse="all"; break;}
            }
        });
        
        
        // OPTIMIZE DOCROOTS OPTION SECTION START
        JLabel optimizeDocrootTraversalsLabel = new JLabel();
        optimizeDocrootTraversalsLabel.setText("Optimize webroot payloads");
        optimizeDocrootTraversalsBox = new JCheckBox();
        //optimizeDocrootTraversalsBox
        optimizeDocrootTraversalsBox.setSelected(true);
        optimizeDocrootTraversalsBox.addItemListener(new ItemListener() {
             @Override
            public void itemStateChanged(ItemEvent e) {
            if(e.getStateChange() == ItemEvent.SELECTED) {//checkbox has been selected
                optimizeDocroots=true;
                
            } else {
                    optimizeDocroots=false;                    
                    };
            }          
        });
        // OPTIMIZE DOCROOTS OPTION SECTION END
        // INCLUDE ABSOLUTE DOCROOTS OPTION SECTION START
        JLabel useAbsoluteDocrootsLabel = new JLabel();
        useAbsoluteDocrootsLabel.setText("Include absolute web roots");
        useAbsoluteWebrootsBox = new JCheckBox();
        //optimizeDocrootTraversalsBox
        useAbsoluteWebrootsBox.setSelected(true);
        useAbsoluteWebrootsBox.addItemListener(new ItemListener() {
             @Override
            public void itemStateChanged(ItemEvent e) {
            if(e.getStateChange() == ItemEvent.SELECTED) {//checkbox has been selected
                useAbsoluteWebroots=true;
                drivesList.setEnabled(true);
                windowsDrivesToUse.setEnabled(true);
            } else {
                    useAbsoluteWebroots=false;
                    drivesList.setEnabled(false);
                    windowsDrivesToUse.setEnabled(false);
                 };
            }          
        });
        useAbsoluteWebrootsBox.setSelected(true);
        // INCLUDE ABSOLUTE DOCROOTS OPTION SECTIONEND        
        
        // LFI MODE OPTION SECTION START        
        JLabel LFIModeLabel = new JLabel();
        LFIModeLabel.setText("LFI mode (don't use web roots)");                
        JCheckBox LFIModeCheckBox = new JCheckBox();
        LFIModeCheckBox.addItemListener(new ItemListener(){
            @Override
            public void itemStateChanged(ItemEvent e) {
            if(e.getStateChange() == ItemEvent.SELECTED) {//checkbox has been selected
                    LFImode=true;
                    LFIOptimizeCheckBox.setEnabled(true);
                    optimizeDocrootTraversalsBox.setEnabled(false);
                    useAbsoluteWebrootsBox.setEnabled(false);
                    drivesList.setEnabled(false);
                    windowsDrivesToUse.setEnabled(false);
            } else {
                        LFImode=false;
                        LFIOptimizeCheckBox.setEnabled(false);
                        optimizeDocrootTraversalsBox.setEnabled(true);
                        useAbsoluteWebrootsBox.setEnabled(true);
                        if(useAbsoluteWebroots)
                        {
                            drivesList.setEnabled(true);
                            windowsDrivesToUse.setEnabled(true);
                        }
                    };
            }  
        });
        // OPTIMIZE DOCROOTS OPTION SECTION END  
        // LFI MODE OPTION SECTION START        
        JLabel LFIoptimization = new JLabel();
        LFIoptimization.setText("Optimize the LFI mode (use longest traversal only)");                
        LFIOptimizeCheckBox = new JCheckBox();
        LFIOptimizeCheckBox.setEnabled(false);
        LFIOptimizeCheckBox.addItemListener(new ItemListener(){
            @Override
            public void itemStateChanged(ItemEvent e) {
            if(e.getStateChange() == ItemEvent.SELECTED) {//checkbox has been selected
                optimizeLFI=true;
            } else {
                    optimizeLFI=false;
                  };
            }  
        });
        //LFI MODE OPTION SECTION END          
       
        
        
        // EVASIVE TECHNIQUES SUPER-SECTION START      
        JLabel evasiveTechniquesLabel = new JLabel();        
        evasiveTechniquesLabel.setText("\nEvasive techniques to use:");
                
        JComboBox evasiveTechniquesToUse = new JComboBox();
        String[] evasiveLabels= { "Default", "Non-recurrent filters", "Breakup-string", "All", "None (disabled)"};
        for (String evasiveName : evasiveLabels) {
            evasiveTechniquesToUse.addItem(evasiveName);
        }
        evasiveList = new JList();
        evasiveTechniquesToUse.addActionListener((ActionEvent e) -> {
            switch(evasiveTechniquesToUse.getSelectedIndex())
            { 
                case 0 : { 
                    this.initiateEvasiveList();
                    break;
                }
                case 1 : { appendListData(evasiveList,this.nonRecurrentEvasiveTraversals); break;}
                case 2 : { appendListData(evasiveList,this.breakupHolderTraversals); break;}
                case 3 : { 
                    appendListData(evasiveList,this.nonRecurrentEvasiveTraversals); 
                    appendListData(evasiveList,this.breakupHolderTraversals);
                    break;
                }
                case 4 : { this.disableEvasiveTechniques(); }
            }
        });  
        evasiveList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);        
        evasiveList.setLayoutOrientation(JList.VERTICAL);
        evasiveList.setVisibleRowCount(10);
        this.initiateEvasiveList();

        
        JScrollPane evasiveListScroller = new JScrollPane(evasiveList);
        evasiveListScroller.setPreferredSize(new Dimension(150, 150));
        
        JButton evasiveRemoveButton = new JButton("Remove");
        evasiveRemoveButton.addActionListener((ActionEvent e) -> {  
                if(evasiveList.getSelectedIndex()!=-1) removeFromListData(evasiveList,(String)evasiveList.getModel().getElementAt(evasiveList.getSelectedIndex()));                                
        });
        JButton evasiveClearButton = new JButton("Clear");
        evasiveClearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disableEvasiveTechniques();
            }
        }); 

        // EVASIVE BREAKUP-STRING MANAGEMENT SECTION START
        JLabel breakupLabel = new JLabel();
        breakupLabel.setText("Breakup strings to replace {BREAK} (asciihex):");
        breakupList = new JList();
        breakupList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);        
        breakupList.setLayoutOrientation(JList.VERTICAL);
        breakupList.setVisibleRowCount(10);
        this.initiateBreakups();
        JScrollPane breakupListScroller = new JScrollPane(breakupList);
        breakupListScroller.setPreferredSize(new Dimension(150, 100));
        
        JButton breakupRemoveButton = new JButton("Remove");
        breakupRemoveButton.addActionListener((ActionEvent e) -> {
                // button callback
                if(breakupList.getSelectedIndex()!=-1) removeFromListData(breakupList,(String)breakupList.getModel().getElementAt(breakupList.getSelectedIndex()));                                
        });        
        
        JLabel newBreakupLabel = new JLabel("Breakup-string (asciihex):");
        JTextArea newBreakupField = new JTextArea(1,20);
        JPanel addBreakupPanel = new JPanel();
        addBreakupPanel.add(newBreakupLabel);
        addBreakupPanel.add(newBreakupField);        

        JButton breakupAddButton = new JButton("Add");
        breakupAddButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // remove all non-asciihex characters
                String val = newBreakupField.getText();
                val=val.replaceAll("[^\\da-f]","");
                
                // make sure the number of characters is even
                int len=val.length();
                if(len%2==0)
                {
                    // add the string to the list
                    String n[]={val};
                    appendListData(breakupList,n);
                    newBreakupField.setText("");
                }
                // break-up strings will be converted from hex to char by the payload generator                                
            }
        });    
        // EVASIVE BREAKUP-STRING MANAGEMENT SECTION STOP

        
        // EVASIVE ENCODING SECTION START
        // TBD
        // EVASIVE ENCODING SECTION STOP
        
        
        // EVASIVE TECHNIQUES SUPER-SECTION STOP
        
        
        
        // WINDOWS DRIVES SECTION START      
        JLabel winDrivesLabel = new JLabel();        
        winDrivesLabel.setText("Windows drive letters to use:");
                
        windowsDrivesToUse = new JComboBox();
        String[] winDrives= { "C","D","E","F","G","H","I","J","All except A,B","All"};
        for (String driveName : winDrives) {
            windowsDrivesToUse.addItem(driveName);
        }
        drivesList = new JList();
        
        windowsDrivesToUse.addActionListener((ActionEvent e) -> {
            switch(windowsDrivesToUse.getSelectedIndex())
            { 
                case 0 : { 
                    String n[]={"C"};
                    appendListData(drivesList,n);
                    break;
                }
                case 1 : { String n[]={"D"}; appendListData(drivesList,n); break;}
                case 2 : { String n[]={"E"}; appendListData(drivesList,n); break;}
                case 3 : { String n[]={"F"}; appendListData(drivesList,n); break;}
                case 4 : { String n[]={"G"}; appendListData(drivesList,n); break;}
                case 5 : { String n[]={"H"}; appendListData(drivesList,n); break;}
                case 6 : { String n[]={"I"}; appendListData(drivesList,n); break;}                
                case 7 : { String n[]={"J"}; appendListData(drivesList,n); break;}
                case 8 : { 
                    String n[]={"C","D","E","F","G","H","I","J","K","L","M","N","O","P","R","S","T","U","W","V","X","Y","Z"};
                    appendListData(drivesList,n); 
                    break;
                }
                case 9 : { 
                    String n[]={"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","R","S","T","U","W","V","X","Y","Z"};
                    appendListData(drivesList,n);
                    break;
                }
            }
        });  
        drivesList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);        
        drivesList.setLayoutOrientation(JList.VERTICAL);
        drivesList.setVisibleRowCount(10);
        String n[]={"C"};
        appendListData(drivesList,n);
        
        JScrollPane drivesListScroller = new JScrollPane(drivesList);
        drivesListScroller.setPreferredSize(new Dimension(250, 80));
        
        JButton drivesRemoveButton = new JButton("Remove");
        drivesRemoveButton.addActionListener((ActionEvent e) -> {
                // button callback
                if(drivesList.getSelectedIndex()!=-1) removeFromListData(drivesList,(String)drivesList.getModel().getElementAt(drivesList.getSelectedIndex()));                
                
        });
        JButton drivesClearButton = new JButton("Clear");
        drivesClearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                clearDrives();
            }
        }); 
        // WINDOWS DRIVES SECTION END
        
        
        
        // NUMBER OF TRAVERSALS SECTION START  
        JLabel numberOfTraversalsLabel = new JLabel();
        numberOfTraversalsLabel.setText("The maximum number of traversals: ");
        JTextArea numberOfTraversals = new JTextArea(1,2);
        numberOfTraversals.setText(Integer.toString(this.defaultMaxTraversalsPerPayload));
        
        numberOfTraversals.addKeyListener(new KeyListener(){
            @Override
            public void keyTyped(KeyEvent e){}
            @Override 
            public void keyPressed(KeyEvent e){}
            @Override
            public void keyReleased(KeyEvent e) {
                try
                {
                    // remove any non-numeric characters first - e.g. if a tab was typed
                    String val = numberOfTraversals.getText();
                    val=val.replaceAll("[^\\d]","");
                    numberOfTraversals.setText(val);
                    int newValue=Integer.parseInt(val);
                    if(newValue<1)
                    {
                        numberOfTraversals.setText(Integer.toString(defaultMaxTraversalsPerPayload)); // invalid value, back to default
                    }
                    else
                    {
                        maxTraversalsPerPayload=newValue;                    
                    }
                }
                catch(Exception e2) // a non-numeric value was typed
                {
                    if(e.getKeyCode()==9||e.getKeyCode()==13)
                    {
                        fileNameField.requestFocusInWindow(); // lose the focus by focusing on another element
                    }
                }
            }
        });
        // NUMBER OF TRAVERSALS SECTION STOP           

        // OPTIONS PANEL START
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel , BoxLayout.Y_AXIS));

        JPanel slashesToUsePanel = new JPanel();
        slashesToUsePanel.add(slashesToUseLabel);
        slashesToUsePanel.add(slashesToUseCombo);

        
        JPanel fileNamePanel=new JPanel();
        fileName = new JLabel("Filename:");
        fileNameField = new JTextArea(1,20);
        fileNameField.setText("a.jpg");
        fileNamePanel.add(fileName);
        fileNamePanel.add(fileNameField);
        optionsPanel.add(slashesToUsePanel);        
        optionsPanel.add(fileNamePanel);

        JPanel optimizeDocrootPanel = new JPanel();
        optimizeDocrootPanel.add(optimizeDocrootTraversalsLabel);
        optimizeDocrootPanel.add(optimizeDocrootTraversalsBox);
        optionsPanel.add(optimizeDocrootPanel);

        JPanel useAbsoluteDocrootsPanel = new JPanel();
        useAbsoluteDocrootsPanel.add(useAbsoluteDocrootsLabel);
        useAbsoluteDocrootsPanel.add(useAbsoluteWebrootsBox);
        optionsPanel.add(useAbsoluteDocrootsPanel);
        
        JPanel LFIPanel = new JPanel();
        LFIPanel.add(LFIModeLabel);
        LFIPanel.add(LFIModeCheckBox);

        JPanel LFIOptimizePanel = new JPanel();
        LFIOptimizePanel.add(LFIoptimization);
        LFIOptimizePanel.add(LFIOptimizeCheckBox);     

        JPanel evasivePanelRight = new JPanel(); 
        evasivePanelRight.setLayout(new BoxLayout(evasivePanelRight, BoxLayout.Y_AXIS));       
        evasivePanelRight.add(evasiveTechniquesToUse);
        evasivePanelRight.add(evasiveListScroller);
        evasivePanelRight.add(evasiveRemoveButton);
        evasivePanelRight.add(evasiveClearButton); 

        JPanel evasivePanel = new JPanel();        
       

   
       


        JPanel numberOfTraversalsPanel = new JPanel();
        numberOfTraversalsPanel.add(numberOfTraversalsLabel);
        numberOfTraversalsPanel.add(numberOfTraversals);
        
        optionsPanel.add(LFIPanel);
        optionsPanel.add(LFIOptimizePanel);
        optionsPanel.add(numberOfTraversalsPanel);    
        optionsPanel.add(evasiveTechniquesLabel);     
        optionsPanel.add(evasivePanelRight);
        optionsPanel.add(evasivePanel);
        
        optionsPanel.add(breakupLabel);
        optionsPanel.add(breakupListScroller);        
        optionsPanel.add(breakupRemoveButton);
        optionsPanel.add(addBreakupPanel);
        optionsPanel.add(breakupAddButton);
        // END OF OPTIONS PANEL
        
        JPanel windowsDrivesPanel = new JPanel();
        windowsDrivesPanel.setLayout(new BoxLayout(windowsDrivesPanel, BoxLayout.Y_AXIS));
        windowsDrivesPanel.add(winDrivesLabel);
        windowsDrivesPanel.add(drivesListScroller);
        windowsDrivesPanel.add(windowsDrivesToUse);
        windowsDrivesPanel.add(drivesRemoveButton);
        windowsDrivesPanel.add(drivesClearButton);
        
        // DOCROOTS PANEL START
        String[] universalDocroots = { "/var/www",
 "/usr/local/httpd", 
 "/usr/local/www",
 "/usr/local/httpd/{TARGET}", 
 "/usr/local/www/{TARGET}",
 "/srv/www", 
 "/var/www/html",
 "/var/www/{TARGET}",
 "/srv/www/{TARGET}", 
 "/var/www/html/{TARGET}",
 "/var/www/vhosts/{TARGET}", 
 "/var/www/virtual/{TARGET}", 
 "/var/www/clients/vhosts/{TARGET}", 
 "/var/www/clients/virtual/{TARGET}"};
        String[] nginxDocroots = {"/var/www/nginx-default"};
        String[] apacheDocroots = { "/usr/local/apache", 
 "/usr/local/apache2", 
  "/usr/local/apache/{TARGET}", 
 "/usr/local/apache2/{TARGET}", 
 "/usr/local/www/apache/{TARGET}", 
 "/usr/local/www/apache24/{TARGET}",
 "/usr/local/{TARGET}/apache/www/apache22/{TARGET}",
 "/usr/local/apache/www/apache22/{TARGET}",
 "/usr/local/{TARGET}/apache/www/apache22/{TARGET}"};
        String[] tomcatDocroots = { "/usr/local/tomcat/webapps/{TARGET}",
 "/usr/local/tomcat01/webapps/{TARGET}", 
 "/usr/local/tomcat02/webapps/{TARGET}",
 "/opt/tomcat5/{TARGET}",
 "/opt/tomcat6/{TARGET}",
 "/opt/tomcat7/{TARGET}",
 "/opt/tomcat8/{TARGET}",
 "/opt/tomcat5/webapps/{TARGET}",
 "/opt/tomcat6/webapps/{TARGET}",
 "/opt/tomcat7/webapps/{TARGET}",
 "/opt/tomcat8/webapps/{TARGET}",
 "/opt/tomcat5/webapps",
 "/opt/tomcat6/webapps",
 "/opt/tomcat7/webapps",
 "/opt/tomcat8/webapps",
 "/var/lib/tomcat7/webapps",
 "/var/lib/tomcat7/webapps/{TARGET}",
 "/var/lib/tomcat8/webapps",
 "/var/lib/tomcat8/webapps/{TARGET}"};
        
String windowsDocroots[] = {       
    "C:/xampp", 
    "C:/Program Files/xampp", 
    "C:/wamp",
    "C:/Program Files/wampp",
    "C:/apache",
    "C:/Program Files/Apache Group/Apache",
    "C:/Program Files/Apache Group/Apache2",
    "C:/Program Files/Apache Group/Apache2.2",
    "C:/Program Files/Apache Group/Apache2.4", 
    "C:/Inetpub/wwwroot",
    "C:/Inetpub/vhosts/",
    "C:/Inetpub/wwwroot/{TARGET}",
    "C:/Inetpub/vhosts/{TARGET}",
    "C:/{TARGET}",  // these last four will require some additional sculpture to support alternative drive letters
     "\\\\.\\GLOBALROOT\\Device\\HArddiskVolume1\\",
     "\\\\?\\localhost\\c$\\",
     "\\\\127.0.0.1\\C$\\",
     "file:///c:\\\\"        
        };

        docrootsLabel = new JLabel("web roots");
        docrootsList = new JList(); //argument has type Object[]
        docrootsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);        
        docrootsList.setLayoutOrientation(JList.VERTICAL);
        docrootsList.setVisibleRowCount(20);
        
        docrootsListScroller = new JScrollPane(docrootsList);
        docrootsListScroller.setPreferredSize(new Dimension(250, 400));
                  
        // list management buttons
        docrootsPasteButton = new JButton("Paste");
        docrootsPasteButton.addActionListener((ActionEvent e) -> {
                // button callback                
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Clipboard clipboard = toolkit.getSystemClipboard();
		String result = "";
                  try {
                    result = (String) clipboard.getData(DataFlavor.stringFlavor);
                   } 
                  catch (UnsupportedFlavorException | IOException ex) {
                    Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                   }
                 docrootsList.setListData(result.split("\\r*\\n"));
            
        });
        docrootsLoadButton = new JButton("Load");
        docrootsLoadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                //Create a file chooser
                JFileChooser fc = new JFileChooser();
                int returnVal = fc.showOpenDialog(PsychoPanel.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) 
                {
                    Scanner inFile1;
                    try {
                        inFile1 = new Scanner(fc.getSelectedFile()).useDelimiter("\\r*\\n");
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                        return;
                    }
                    List<String> temps = new ArrayList<>();
                    while (inFile1.hasNext()) 
                    {
                        String line = inFile1.next();
                        temps.add(line);
                    }
                    inFile1.close();
                    docrootsList.setListData(temps.toArray());
                }                
            }
        });
        docrootsRemoveButton = new JButton("Remove");
        docrootsRemoveButton.addActionListener((ActionEvent e) -> {
                // button callback
                if(docrootsList.getSelectedIndex()!=-1) removeFromListData(docrootsList,(String)docrootsList.getModel().getElementAt(docrootsList.getSelectedIndex()));                
                
        });
        docrootsClearButton = new JButton("Clear");
        docrootsClearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                String [] empty = {};
                docrootsList.setListData(empty); //for some weird reason removeAll did not want to work.
            }
        });   
        JTextField docrootsAddField = new JTextField(8);
        
        docrootsAddButton = new JButton("Add");
        docrootsAddButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                String [] tmp = {docrootsAddField.getText()};
                appendListData(docrootsList,tmp);
            }
        });    
        docrootsAddLists = new JComboBox();
        docrootsAddLists.addItem("Add from list ...");
        String[] docrootListNames= { "Universal", "Nginx", "Apache", "Tomcat","Windows","All"};
        for (String docrootListName : docrootListNames) {
            docrootsAddLists.addItem(docrootListName);
        }
        docrootsAddLists.addActionListener((ActionEvent e) -> {
            switch(docrootsAddLists.getSelectedIndex())
            { 
                case 1 : { appendListData(docrootsList,universalDocroots); break;}
                case 2 : { appendListData(docrootsList,nginxDocroots); break;}
                case 3 : { appendListData(docrootsList,apacheDocroots); break;}
                case 4 : { appendListData(docrootsList,tomcatDocroots); break;}
                case 5 : { appendListData(docrootsList,windowsDocroots); break; }
                case 6 : { // "All"
                    
                    appendListData(docrootsList,universalDocroots); 
                    appendListData(docrootsList,nginxDocroots); 
                    appendListData(docrootsList,apacheDocroots);
                    appendListData(docrootsList,tomcatDocroots);
                    appendListData(docrootsList,windowsDocroots);
                    break;
                }
            }
        });
        appendListData(docrootsList,universalDocroots); 
        //appendListData(docrootsList,nginxDocroots); 
        appendListData(docrootsList,apacheDocroots);
        //appendListData(docrootsList,tomcatDocroots);
        
        docrootsPanelRight.add(docrootsLabel);
        docrootsPanelRight.add(docrootsListScroller);
        docrootsPanelRight.add(docrootsAddLists);
        docrootsPanelRight.add(windowsDrivesPanel);

        docrootsPanelLeft.add(docrootsPasteButton);
        docrootsPanelLeft.add(docrootsLoadButton);
        docrootsPanelLeft.add(docrootsRemoveButton);
        docrootsPanelLeft.add(docrootsClearButton);
        JPanel docrootsPanelLeftAdd = new JPanel();
        
        docrootsPanelLeftAdd.add(docrootsAddButton);
        docrootsPanelLeftAdd.add(docrootsAddField);
        docrootsPanelLeft.add(docrootsPanelLeftAdd);
        
        docrootsPanel.add(docrootsPanelLeft);
        docrootsPanel.add(docrootsPanelRight);        
        // DOCROOTS PANEL STOP

        // SUFFIXES PANEL START
       // suffixes PANEL START
        String[] suffixes= {"","html", "htdocs", "httpdocs", "php", "public", "src", "site", "build", "web", "data", "sites/all", "www/build"};
        //String[] suffixes = {""};
        // the default list of suffixes is currently global, although it is quite apache-specific, might be worth clearing them once a non-apache webroots are choosen
        // 
   
        suffixesLabel = new JLabel("Suffixes");
        suffixesList = new JList(); //argument has type Object[]
        suffixesList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);        
        suffixesList.setLayoutOrientation(JList.VERTICAL);
        suffixesList.setVisibleRowCount(20);
        
       
        suffixesListScroller = new JScrollPane(suffixesList);
        suffixesListScroller.setPreferredSize(new Dimension(100, 100));
                  
        // list management buttons
        suffixesPasteButton = new JButton("Paste");
        suffixesPasteButton.addActionListener((ActionEvent e) -> {
                // button callback                
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Clipboard clipboard = toolkit.getSystemClipboard();
		String result = "";
                  try {
                    result = (String) clipboard.getData(DataFlavor.stringFlavor);
                   } 
                  catch (UnsupportedFlavorException ex) {
                    Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                   } catch (IOException ex) {
                  Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                 }
                 suffixesList.setListData(result.split("\\r*\\n"));
            
        });
        suffixesLoadButton = new JButton("Load");
        suffixesLoadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                //Create a file chooser
                JFileChooser fc = new JFileChooser();
                int returnVal = fc.showOpenDialog(PsychoPanel.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) 
                {
                    Scanner inFile1;
                    try {
                        inFile1 = new Scanner(fc.getSelectedFile()).useDelimiter("\\r*\\n");
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                        return;
                    }
                    List<String> temps = new ArrayList<>();
                    while (inFile1.hasNext()) 
                    {
                        String line = inFile1.next();
                        temps.add(line);
                    }
                    inFile1.close();
                    suffixesList.setListData(temps.toArray());
                }                
            }
        });
        suffixesRemoveButton = new JButton("Remove");
        suffixesRemoveButton.addActionListener((ActionEvent e) -> {
                // button callback
                if(suffixesList.getSelectedIndex()!=-1) removeFromListData(suffixesList,(String)suffixesList.getModel().getElementAt(suffixesList.getSelectedIndex()));                
                
        });
        suffixesClearButton = new JButton("Clear");
        suffixesClearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                String [] empty = {};
                suffixesList.setListData(empty); //for some weird reason removeAll did not want to work.
            }
        });   
        JTextField suffixesAddField = new JTextField(8);
        suffixesAddButton = new JButton("Add");
        suffixesAddButton.addActionListener((ActionEvent e) -> {
                String [] tmp = {suffixesAddField.getText()};
                appendListData(suffixesList,tmp);
        });    
     
        suffixesList.setListData(suffixes);
        suffixesPanelRight.add(suffixesLabel);
        suffixesPanelRight.add(suffixesListScroller);
              

        suffixesPanelLeft.add(suffixesPasteButton);
        suffixesPanelLeft.add(suffixesLoadButton);
        suffixesPanelLeft.add(suffixesRemoveButton);
        suffixesPanelLeft.add(suffixesClearButton);
        JPanel suffixesPanelLeftAdd = new JPanel();
        suffixesPanelLeftAdd.add(suffixesAddButton);
        suffixesPanelLeftAdd.add(suffixesAddField);        
        suffixesPanelLeft.add(suffixesPanelLeftAdd);
        
        suffixesPanel.add(suffixesPanelLeft);
        suffixesPanel.add(suffixesPanelRight); 
        // SUFFIXES PANEL STOP
        
        // TARGETS PANEL START
      
        // this needs to be replaced with a list populated from the scope and from the intruder content...  intruder content is better
        // 1) we need to fetch the domain
        // 2) based on the domain, we generate the list of targets ("Send to psychoPATH" menu option?)
        // 3) we use the domain in querying the site map (better than using the scope, for many reasons)
       
   
        targetsLabel = new JLabel("Targets");
        targetsList = new JList(); //argument has type Object[]
        targetsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);        
        targetsList.setLayoutOrientation(JList.VERTICAL);
        targetsList.setVisibleRowCount(10);
       
        targetsListScroller = new JScrollPane(targetsList);
        targetsListScroller.setPreferredSize(new Dimension(250, 250));
                  
        // list management buttons
        targetsPasteButton = new JButton("Paste");
        targetsPasteButton.addActionListener((ActionEvent e) -> {
                // button callback                
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Clipboard clipboard = toolkit.getSystemClipboard();
		String result = "";
                  try {
                    result = (String) clipboard.getData(DataFlavor.stringFlavor);
                   } 
                  catch (UnsupportedFlavorException ex) {
                    Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                   } catch (IOException ex) {
                  Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                 }
                 targetsList.setListData(result.split("\\r*\\n"));
            
        });
        targetsLoadButton = new JButton("Load");
        targetsLoadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                //Create a file chooser
                JFileChooser fc = new JFileChooser();
                int returnVal = fc.showOpenDialog(PsychoPanel.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) 
                {
                    Scanner inFile1;
                    try {
                        inFile1 = new Scanner(fc.getSelectedFile()).useDelimiter("\\r*\\n");
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(PsychoPanel.class.getName()).log(Level.SEVERE, null, ex);
                        return;
                    }
                    List<String> temps = new ArrayList<>();
                    while (inFile1.hasNext()) 
                    {
                        String line = inFile1.next();
                        temps.add(line);
                    }
                    inFile1.close();
                    targetsList.setListData(temps.toArray());
                }                
            }
        });
        targetsRemoveButton = new JButton("Remove");
        targetsRemoveButton.addActionListener((ActionEvent e) -> {
                // button callback
                if(targetsList.getSelectedIndex()!=-1) removeFromListData(targetsList,(String)targetsList.getModel().getElementAt(targetsList.getSelectedIndex()));                
                
        });
        targetsClearButton = new JButton("Clear");
        targetsClearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // button callback
                String [] empty = {};
                targetsList.setListData(empty); //for some weird reason removeAll did not want to work.
            }
        });   
        JTextField targetesAddField = new JTextField(8);
        targetsAddButton = new JButton("Add");
        targetsAddButton.addActionListener((ActionEvent e) -> {
                String [] tmp = {targetesAddField.getText()};
                appendListData(targetsList,tmp);
        });    
        // logOutput = new JTextArea(20,50);
        this.updateScope(this.proto,this.hostname); 
        
        targetsPanelRight.add(targetsLabel);
        targetsPanelRight.add(targetsListScroller);
              

        targetsPanelLeft.add(targetsPasteButton);
        targetsPanelLeft.add(targetsLoadButton);
        targetsPanelLeft.add(targetsRemoveButton);
        targetsPanelLeft.add(targetsClearButton);
        JPanel targetsPanelLeftAdd = new JPanel();
        targetsPanelLeftAdd.add(targetsAddButton);
        targetsPanelLeftAdd.add(targetesAddField);        
        targetsPanelLeft.add(targetsPanelLeftAdd);
        
        targetsPanel.add(targetsPanelLeft);
        targetsPanel.add(targetsPanelRight);         
        // TARGETTS PANEL STOP
        // LOG PANEL START
        //JLabel logLabel = new JLabel("Output log:");
        //logPanel.add(logLabel);
        //logPanel.add(logOutput);
        // LOG PANEL STOP
        
        
        // ADD PANELS TO THE MAIN PSYCHO PANEL
        
        pathsPanel.add(optionsPanel);
        pathsPanel.add(docrootsPanel);
        pathsPanel.add(suffixesPanel);
        pathsPanel.add(targetsPanel);
        
        mainPanel.add(pathsPanel); // first row
        
        add(mainPanel,BorderLayout.NORTH);
        addHierarchyListener((HierarchyEvent evt) -> {
            PsychoPATH.PsychoTab.findTab();
        });        
    }
    public void logOutput(String msg)
    {
        //this.logOutput.append(msg);
        stdout.println(msg);
    }

}
