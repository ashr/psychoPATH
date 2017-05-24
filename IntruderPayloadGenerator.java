package uk.co.pentest.psychoPATH;

import burp.BurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IHttpRequestResponse;
import burp.IIntruderPayloadGenerator;
import flex.messaging.util.StringUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.swing.ListModel;

/**
 *
 * @author julianh
 */
public final class IntruderPayloadGenerator implements IIntruderPayloadGenerator 
{

    ArrayList<String> psychopaths; // the final payloads
    int payloadIndex; // counter for the payload mark       
    String payloadType; // path or mark
    IBurpExtenderCallbacks callbacks = BurpExtender.getBurpCallbacks();
    ArrayList<String> directoriesToCheck; // this is for the verification phase
    int payloadMarkerLength=7; // the lenght of the payload marker, a fixed length is safer while injecting into images etc.
    PsychoTab tab;

    public IntruderPayloadGenerator(String payloadType, PsychoTab tab) 
    {
        this.payloadType = payloadType;
        this.tab=tab;
        if(payloadType!="check") 
        {
            Set<String> targetDocroots; // this is a merge of the prefixes and targets
            ArrayList<String> brutDocrootSuffixes; // we'll also merge all targets into this
            ArrayList<String> traversals;
      
            ArrayList<String> unitTraversalsToUse;
            
            ArrayList<String> slashesToUse = new ArrayList<>();
            
            if(this.tab.psychoPanel.slashesToUse=="win")
            {
                slashesToUse.add("\\");
            }
            if(this.tab.psychoPanel.slashesToUse=="nix")
            {
                slashesToUse.add("/");
            }
            if(this.tab.psychoPanel.slashesToUse=="all")
            {
                slashesToUse.add("\\");
                slashesToUse.add("/");
            }
            
            
            // load basic traversals, according to the set of slashes to use
            unitTraversalsToUse = new ArrayList<>();
            for(int i=0;i<this.tab.psychoPanel.basicTraversals.size();i++)
            {
                unitTraversalsToUse.add(this.tab.psychoPanel.basicTraversals.get(i));

            }
            // load the evasive traversals, according to the set of slashes to use
            if(this.tab.psychoPanel.evasiveTechniques)
            {
                // read and decode the list of currently loaded breakup strings, if there are any
                ListModel breakupModel = this.tab.psychoPanel.breakupList.getModel();
                ArrayList<String> breakupStrings = new ArrayList<>();
                for(int i=0;i<breakupModel.getSize();i++)
                {
                    String asciihex=breakupModel.getElementAt(i).toString();
                    // ok, now we need to convert it back to characters and store in the breakupTraversals array
                    StringBuilder output = new StringBuilder();
                    for (int j = 0; j < asciihex.length(); j+=2) 
                    {
                        String str = asciihex.substring(j,j+2);
                        output.append((char)Integer.parseInt(str,16));
                    }
                    breakupStrings.add(output.toString());
                }
                
                ListModel evasiveTravModel = this.tab.psychoPanel.evasiveList.getModel();
                for (int i=0; i<evasiveTravModel.getSize(); i++) 
                {
                    String evasiveTraversal=evasiveTravModel.getElementAt(i).toString();
                    if(evasiveTraversal.contains("{BREAK}")) // we are dealing with a break-up sequence
                    {
                        // iterate over break-up strings and create variations
                        for(int j=0;j<breakupStrings.size();j++)
                        {
                            unitTraversalsToUse.add(evasiveTraversal.replace("{BREAK}", breakupStrings.get(j)));
                        }
                        // otherwise the traversal is ignored (not added to the unitTraversalsToUse arr list)
                    }
                    else
                    {
                        unitTraversalsToUse.add(evasiveTraversal);
                    }
                }  
 
            }
            String fileName;
            PsychoPanel panel=tab.getUiComponent();                        
            if(psychopaths==null) psychopaths = new ArrayList<>();
            if(!psychopaths.isEmpty()) psychopaths.clear();
            // generate all the payloads and put them into the arr

            targetDocroots = new HashSet<String>();
            brutDocrootSuffixes = new ArrayList<String>();
            traversals = new ArrayList<String>(); 
            // 0) populate traversals and the filename           
            ArrayList<String> longestTraversals = new ArrayList<>();
            for(int i=0;i<unitTraversalsToUse.size();i++)
            {
                String baseTraversal = unitTraversalsToUse.get(i);
                String traversal=baseTraversal;
                for(int j=0;j<this.tab.psychoPanel.maxTraversalsPerPayload;j++)
                {
                    traversals.add(traversal);
                    if(j==this.tab.psychoPanel.maxTraversalsPerPayload-1) longestTraversals.add(traversal);
                    traversal=traversal+baseTraversal;
                }
            }
                    
            fileName=panel.fileNameField.getText();
      
            // 1) copy @brute_doc_root_prefixes to @target_docroots
            if(panel.LFImode==false) // whether to use the webroots at all
            {
                ListModel docListModel = panel.docrootsList.getModel();
            //if(docListModel==null) this.panel.stdout.println("The thing is empty...");
            
                for (int i=0; i<docListModel.getSize(); i++) 
                {
                    String targetDocroot=docListModel.getElementAt(i).toString();
                    // if the targetDocroot contains the {TARGET} holder
               
                    if(targetDocroot.contains("{TARGET}"))
                    {
                        // iterate over the targets 
                        // and create corresponding versions of the targetDocroot by substitution
                        ListModel targetListModel = panel.targetsList.getModel();
                   
                        for(int j=0;j<targetListModel.getSize();j++)
                        {
                             String target=targetListModel.getElementAt(j).toString();
                             String newTargetDocroot=targetDocroot.replace("{TARGET}",target);
                             targetDocroots.add(newTargetDocroot);
                        } 
                                        
                    }
                     // otherwise simply copy the targetDocroot                
                     else
                    {
                     targetDocroots.add(targetDocroot);     
                    }                
                }             
                // add the empty suffix
                brutDocrootSuffixes.add("");
                // 2.1) copy @targets to @brut_doc_suffixes
                ListModel targetListModel = panel.targetsList.getModel();
                for(int i=0;i<targetListModel.getSize();i++)
                {
                   String target=targetListModel.getElementAt(i).toString();
                   brutDocrootSuffixes.add(target);
                }                 
                // 2.2) copy @suffixes to @brut_doc_suffixes
                ListModel suffixListModel = panel.suffixesList.getModel();
                for(int i=0;i<suffixListModel.getSize();i++)
                {
                    String suffix=suffixListModel.getElementAt(i).toString();
                    brutDocrootSuffixes.add(suffix);
                }
                // 3.1) iterate through @targetDocroots -> 
                //        3.2)   iterate through @brute_doc_root_suffixes -> 
            //                3.3)      iterate through traversals -> 
            //                     3.4)              generate psychopaths
            }
            // 3.4.1 the bare filename with no prepended path injections
            psychopaths.add(fileName);             
            // 3.4.2 the pure traversal + filename permutations (for upload directories hidden within the document root and LFI mode)
            if(panel.LFImode==true&&panel.optimizeLFI==true)
            {
                for(int i=0;i<longestTraversals.size();i++)        // 3.3
                {
                    String payload=longestTraversals.get(i)+"/"+fileName;
                    for(int j=0;j<slashesToUse.size();j++)
                    {
                        psychopaths.add(payload.replace("/",slashesToUse.get(j)));      
                    }                    
                }
            }
            else
            {
                for(int i=0;i<traversals.size();i++)
                {
                    String payload=traversals.get(i)+"/"+fileName;
                    for(int j=0;j<slashesToUse.size();j++)
                    {
                        psychopaths.add(payload.replace("/",slashesToUse.get(j))); 
                    }
                }
            }

            // 3.4.3 the targetDocroot+brutDocrootsuffix permutations 
            for(String targetDocRoot : targetDocroots) // 3.1
            {
                for(int i=0;i<brutDocrootSuffixes.size();i++) // 3.2
                {     
                    if(this.tab.psychoPanel.optimizeDocroots)
                    {
                        for(int j=0;j<longestTraversals.size();j++)        // 3.3
                        {
                          // if the docroot is windows-specific, we skip the letter for the traversal for it to work
                          String payload=longestTraversals.get(j)+targetDocRoot.replace("C:","")+"/"+brutDocrootSuffixes.get(i)+"/"+fileName;
                          for(int k=0;k<slashesToUse.size();k++)
                          {
                                psychopaths.add(payload.replace("/",slashesToUse.get(k)));   
                          }
                        }
                    }
                    else
                    {
                        for(int j=0;j<traversals.size();j++)        // 3.3
                        {
                          String payload=traversals.get(j)+targetDocRoot.replace("C:","")+"/"+brutDocrootSuffixes.get(i)+"/"+fileName;
                          for(int k=0;k<slashesToUse.size();k++)
                          {
                                psychopaths.add(payload.replace("/",slashesToUse.get(k)));
                          }
                        }
                    }
                    if(this.tab.psychoPanel.useAbsoluteWebroots)
                    {
                         ListModel drivesModel = panel.drivesList.getModel();                                                                          
                         String payload=targetDocRoot+"/"+brutDocrootSuffixes.get(i)+"/"+fileName;
                         for(int j=0;j<slashesToUse.size();j++)
                         {
                                String docroot=payload.replace("/",slashesToUse.get(j));
                                if(docroot.startsWith("C:"))
                                {
                                    for(int k=0;k<drivesModel.getSize();k++)
                                    {
                                         // if we are dealing with windows, we nee to make sure we use all drive the letters configured                                    
                                         psychopaths.add(docroot.replace("C:",drivesModel.getElementAt(k).toString()+":"));                                 
                                    }  
                                }
                                else
                                {
                                    psychopaths.add(docroot);
                                }
                         }                       
                    }
                }
            }                

        }
        if("check".equals(payloadType)&&directoriesToCheck==null)
        {
            directoriesToCheck=new ArrayList<>();
            directoriesToCheck=tab.psychoPanel.genericSuffixes; // we simply steal this list :)
        }
    }
    @Override
    public boolean hasMorePayloads() 
    {
        if("check".equals(payloadType))
        {
            return payloadIndex<directoriesToCheck.size();
        }
        return payloadIndex < psychopaths.size();       
    }

    @Override
    public byte[] getNextPayload(byte[] baseValue) 
    {                
        byte[] payload = new byte[0];
        if(payloadType!="check")
        {
            if(payloadType=="mark")
            {
                // return the payload mark, which is simply a unique string (number -> index)
                String prefix="";
                int ln = this.payloadMarkerLength-Integer.toString(payloadIndex).length();
                for(int i=0;i<ln;i++) prefix=prefix+"0";
                payload=callbacks.getHelpers().stringToBytes(prefix+Integer.toString(payloadIndex));
            }
            else
            {
                // return the actual payload
                payload = callbacks.getHelpers().stringToBytes(psychopaths.get(payloadIndex).toString());   
            }

        }
        else
        {
            payload = callbacks.getHelpers().stringToBytes(directoriesToCheck.get(payloadIndex).toString()+"/"+tab.psychoPanel.fileNameField.getText()); 
        }
        payloadIndex++; // increase the index
        return payload;
    }
    @Override
    public void reset() 
    {
        payloadIndex = 0;
        if(payloadType!="check")
        {
            psychopaths.clear();
        }
        else
        {
            directoriesToCheck.clear();
        }
    }       
}