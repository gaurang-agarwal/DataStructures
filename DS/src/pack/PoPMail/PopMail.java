import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;

import javax.activation.MimetypesFileTypeMap;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;

import net.freeutils.tnef.TNEF;
import net.freeutils.tnef.TNEFInputStream;
import net.freeutils.tnef.TNEFUtils;

import org.apache.log4j.Logger;

import com.appnetix.jobs.util.PortalUtils;
import com.appnetix.jobs.util.StringUtil;
import com.appnetix.jobs.util.information.Info;
import com.appnetix.jobs.util.mail.PopMailUtil;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.pop3.POP3Folder;

/**
 * This Class contails the method which will connect to the specified POP3 Server
 * Read the all mails from POP3 server and find out the New Mails
 * Update the records for new mails.
 */
public class PopMail {
    static Logger logger = Logger.getLogger(PopMail.class);
    private static PopMail instance = null;
    private static ArrayList mailAddedFranchisee = null;

    /**
     * This method connect to the POP mail server and return the mail contents
     */

    public ArrayList receive(String popServer, String popUser, String popPassword, Connection con, String contextPath) {
        //FIM_Capture_Optimization starts
        logger.info("Receiving Mails**********************popServer*****" + popServer + "**popUser********" + popUser + "***popPassword*******" + popPassword);
        // Store Class  that models a message store and its access protocol, for storing and retrieving messages
        ArrayList mailInfoList = new ArrayList();
        Info emailIdInfo = getFranchiseeEmailId(con);
        PopMailUtil popUtil = null;
        try {
            Message[] msgs = null;
            popUtil = new PopMailUtil();
            String connectedTo = popUtil.connectToServer(popServer, popUser, popPassword);
            if ("".equals(connectedTo)) {
                System.out.println("************Unable to Connect to Server***************");
            } else {
                msgs = popUtil.getMessage(2);
            }
            if (msgs == null) {
                System.out.println("************Error In Getting message***************");
            }
            System.out.println("**TOTAL MESSAGES AT INBOX***" + msgs.length);
            //FIM_Capture_Optimization ends
            /**
             *Getting the list of messageIds from The from FIM_EXTERNAL_MAILS which are already inserted in Contact History.
             */
            ArrayList messageList = getInstancePopMail().getMessageIds(con);
            String messageId = null;
            if (messageList != null) {
                String mailSubject = null;
                int length = msgs.length;
                /** Reading all mails*/
                for (int msgNum = 0; msgNum < length; msgNum++) {
                    mailAddedFranchisee = new ArrayList();
                    try {
                        /**Reteriving the Message subject */
                        mailSubject = msgs[msgNum].getSubject();
                    } catch (MessagingException ex) {
                        logger.info("Exception in" + ex.getMessage());
                        continue;
                    }
                    /**
                     Return the unique ID string for this message, or null if not available. Uses
                     the POP3 UIDL command.
                     */
                    //FIM_Capture_Optimization starts
                    try {
                        if ("imap".equals(connectedTo)) {
                            messageId = String.valueOf(((IMAPFolder) popUtil.getFolder()).getUID(msgs[msgNum]));
                        } else {
                            messageId = ((POP3Folder) popUtil.getFolder()).getUID(msgs[msgNum]);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //FIM_Capture_Optimization ends
                    if (messageList.contains(messageId))//Message already stored
                    {
                        continue;
                    }
                    /**
                     Compare the Message ID in the database with the Read mail UID
                     *if not find in datbase add  in the newMeassageId List
                     */
                    int ccLength = 0;
                    int toLength = 0;
                    if (msgs[msgNum].getRecipients(Message.RecipientType.CC) != null)
                        ccLength = msgs[msgNum].getRecipients(Message.RecipientType.CC).length;
                    if (msgs[msgNum].getRecipients(Message.RecipientType.TO) != null)
                        toLength = msgs[msgNum].getRecipients(Message.RecipientType.TO).length;
                    String ccCSV = "";
                    String toCsv = "";
                    Info emailMessageIDInfo = new Info();
                    for (int k = 0; k < ccLength; k++) {
                        String cc = ((InternetAddress) msgs[msgNum].getRecipients(Message.RecipientType.CC)[k]).getAddress();
                        if (!popUser.equals(cc))//Remove Pop Server emailId
                            ccCSV = ccCSV.concat(cc).concat(",");
                        if (StringUtil.isValidEmailAddress(cc)) {
                            emailMessageIDInfo.set(cc, messageId);  //LaBoit-20120321-146
                        }
                    }
                    for (int k = 0; k < toLength; k++) {
                        String to = ((InternetAddress) msgs[msgNum].getRecipients(Message.RecipientType.TO)[k]).getAddress();
                        if (!popUser.equals(to))//Remove Pop Server emailId
                            toCsv = toCsv.concat(to).concat(",");
                        if (StringUtil.isValidEmailAddress(to)) {
                            emailMessageIDInfo.set(to, messageId); //LaBoit-20120321-146
                        }
                    }

                    if (ccCSV.length() != 0) {
                        ccCSV = ccCSV.substring(0, ccCSV.length() - 1);
                    }
                    if (toCsv.length() != 0) {
                        toCsv = toCsv.substring(0, toCsv.length() - 1);
                    }
                    if (toCsv.equals("")) {
                        toCsv = ccCSV;
                        ccCSV = "";
                    }
                    Iterator it = emailMessageIDInfo.getKeySetIterator();
                    while (it.hasNext()) {
                        String email = (String) it.next();
                        //Demo_ISSUE_7012 starts
                        if (email != null)
                            email = email.toLowerCase();
                        //Demo_ISSUE_7012 ends.
                        Part messagePart = msgs[msgNum];
                        if (emailIdInfo.getObject(email) != null && !messageList.contains(messageId)) // If email id found in Franchisee
                        {
                            ArrayList arr = ((ArrayList) emailIdInfo.getObject(email));
                            for (int i = 0; i < arr.size(); i++) {
                                String franNo = (String) arr.get(i);
                                if (mailAddedFranchisee.contains(franNo)) {
                                    ((ArrayList) emailIdInfo.getObject(email)).remove(franNo);
                                    i--;
                                }
                            }
                            if (((ArrayList) emailIdInfo.getObject(email)).size() == 0)// After removing franchisee Number if count is zero.
                                continue;
                            String fileName = "";
                            String fileNameForDB = "";
                            String fileContentType = "";
                            Object content = msgs[msgNum].getContent();
                            if (content instanceof Multipart)// Attachment Found in Mail
                            {
                                messagePart = ((Multipart) content).getBodyPart(0);
                                while (messagePart.getContent() instanceof Multipart)
                                    messagePart = ((Multipart) messagePart.getContent()).getBodyPart(0);
                                Multipart multipart = (MimeMultipart) content;
                                for (int i = 0, n = multipart.getCount(); i < n; i++) {
                                    Part part = multipart.getBodyPart(i);

                                    //P_C_FIM_MAIL_PARSING
                                    String disposition = null;
                                    try {
                                        disposition = part.getDisposition();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    //P_C_FIM_MAIL_PARSING
                                    if (disposition != null && (disposition.equals(Part.ATTACHMENT) || disposition.equals(Part.INLINE))) {
                                        fileName = part.getFileName();
                                        if (fileName != null && !fileName.equals("null") && fileName.lastIndexOf(".") != -1) {
                                            fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "_" + Long.toString(System.currentTimeMillis()) + fileName.substring(fileName.lastIndexOf("."));
                                            fileNameForDB = fileNameForDB + fileName + ",";
                                        }
                                        File fileObj = new File("/home/tomcat/Documents/" + contextPath + "/fim");
                                        if (!fileObj.exists()) {
                                            fileObj.mkdirs();
                                        }
                                        if (fileName != null && !fileName.equals("null") && fileName.lastIndexOf(".") != -1 && part.getContentType() != null && part.getContentType().indexOf(';') != -1)
                                            fileContentType = fileContentType + part.getContentType().substring(0, part.getContentType().indexOf(';')) + ",";
                                        else if (fileName != null && !fileName.equals("null") && fileName.lastIndexOf(".") != -1)
                                            fileContentType = fileContentType + "text/html,";

                                        if (TNEFUtils.isTNEFMimeType(part.getContentType().toString())) {
                                            fileNameForDB = "";
                                            fileContentType = "";
                                            final TNEFInputStream tnefInputStream = new TNEFInputStream(part.getInputStream());

                                            File fileObjTnf = new File("/home/tomcat/Documents/" + contextPath + "/fim/tempTnf");
                                            if (!fileObjTnf.exists()) {//Create Directory if Not Found
                                                fileObjTnf.mkdirs();
                                            }
                                            int numberOfattachments = TNEF.extractContent(tnefInputStream, "/home/tomcat/Documents/" + contextPath + "/fim/tempTnf");

                                            File oldFile = new File("/home/tomcat/Documents/" + contextPath + "/fim/tempTnf");
                                            File dirD = new File("/home/tomcat/Documents/" + contextPath + "/fim");

                                            String[] dirFiles = oldFile.list();
                                            if (dirFiles != null) {
                                                for (int j = 0; j < dirFiles.length; j++) {
                                                    File delFile = new File("/home/tomcat/Documents/" + contextPath + "/fim/tempTnf" + "/" + dirFiles[j]);
                                                    fileContentType = fileContentType + new MimetypesFileTypeMap().getContentType(delFile) + ",";
                                                    logger.info("moving file :" + dirFiles[j]);
                                                    fileName = dirFiles[j];
                                                    fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "_" + Long.toString(System.currentTimeMillis()) + fileName.substring(fileName.lastIndexOf("."));
                                                    fileNameForDB = fileNameForDB + fileName + ",";
                                                    moveFile(delFile, "/home/tomcat/Documents/" + contextPath + "/fim/", fileName);
                                                    delFile.delete();
                                                }
                                            }
                                        } else
                                            saveFile("/home/tomcat/Documents/" + contextPath + "/fim/" + fileName, part.getInputStream());
                                    }
                                }
                                if (fileNameForDB != null && fileNameForDB.length() != 0)
                                    fileNameForDB = fileNameForDB.substring(0, fileNameForDB.length() - 1);
                                if (fileContentType != null && fileContentType.length() != 0)
                                    fileContentType = fileContentType.substring(0, fileContentType.length() - 1);
                            }
                            insertMessageIds(((InternetAddress) msgs[msgNum].getFrom()[0]).getAddress(), toCsv, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(msgs[msgNum].getSentDate()), mailSubject, messagePart.getContent().toString(), fileNameForDB, (ArrayList) emailIdInfo.getObject(email), ccCSV, messageId, fileContentType, con);
                        }
                    }
                    //    mailToBeProccesInOneRound++;
                } // For loop ENDs

            }
        } catch (Exception ex) {
            logger.error(ex);
            ex.printStackTrace();
        } finally {
            //FIM_Capture_Optimization
            if (popUtil != null) {
                popUtil.closeConnection();
            }//FIM_Capture_Optimization
        }

        return mailInfoList;
    }

    /**
     * Returns a list of messageIds from The from FIM_EXTERNAL_MAILS
     */
    public ArrayList getMessageIds(Connection con) {
        ArrayList IDs = new ArrayList();
        try {
            PreparedStatement pstmt = con.prepareStatement("SELECT FIM_CAPTURE_MAIL_ID FROM FIM_EXTERNAL_MAILS WHERE FIM_CAPTURE_MAIL_ID!=''");
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                IDs.add(rs.getString("FIM_CAPTURE_MAIL_ID"));
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return IDs;
    }

    /**
     * Insert the Detail of Mail in Contact History of FIM
     */
    public void insertMessageIds(String fromMailId, String toMailIds, String date, String mailSubject, String mailText, String fileName, ArrayList franchiseeNumber, String ccMailIds, String captureMailId, String fileContentType, Connection con) {
        for (int i = 0; i < franchiseeNumber.size(); i++) {
            String query = "INSERT INTO FIM_EXTERNAL_MAILS(MAIL_NO,MAIL_FROM,MAIL_TO,MAIL_DATE,MAIL_SUBJECT,MAIL_MESSAGE,MAIL_CC,STATUS,FIM_CAPTURE_MAIL_ID,MAIL_SENT_BY,FRAN_NO,ATTACHMENT_FILE_NAME,ATTACHMENT_TYPE,FORMAT_OF_MAIL,TAB_NAME) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try {
                PreparedStatement st = con.prepareStatement(query);
                st.setString(1, "null");
                st.setString(2, fromMailId);
                st.setString(3, toMailIds);
                st.setString(4, date);
                st.setString(5, PortalUtils.toHex(mailSubject));
                st.setString(6, mailText);
                st.setString(7, ccMailIds);
                st.setString(8, "1");
                st.setString(9, captureMailId);
                st.setString(10, "-1");
                st.setString(11, (String) franchiseeNumber.get(i));
                st.setString(12, fileName);
                st.setString(13, fileContentType);
                st.setString(14, "FCK Editor");
                st.setString(15, "FIM Fetch");
                st.executeUpdate();
                mailAddedFranchisee.add((String) franchiseeNumber.get(i));
            } catch (Exception e) {
                logger.info(e);
            }
        }
    }

    /**
     * Making a singleton class oblect of BounceMailJob
     */

    public static PopMail getInstancePopMail() {
        if (instance == null) {
            instance = new PopMail();
        }
        return instance;
    }
    //Getting the mail IDs of Franchisee and Franchisee NO.

    public Info getFranchiseeEmailId(Connection con) {
        Info info = null;
        ArrayList franchiseeNo = null;
        try {
            info = new Info();
            PreparedStatement pstmt = con.prepareStatement("SELECT EMAIL_ID,FRANCHISEE_NO,FRANCHISEE_NAME,STORE_EMAIL,LEGAL_NOTICE_EMAIL FROM FRANCHISEE WHERE IS_FRANCHISEE='Y' ;");
            ResultSet result = pstmt.executeQuery();
            String[] emailIDArr = null;
            while (result.next()) {
                //If more Franchisee have same Mail Ids
                emailIDArr = null;
                if (StringUtil.isValidNew(result.getString("EMAIL_ID"))) {
                    emailIDArr = result.getString("EMAIL_ID").replaceAll(";", ",").replaceAll(" ", "").split(",");
                }

                if (emailIDArr != null) {
                    for (int i = 0; i < emailIDArr.length; i++) {
                        if (emailIDArr[i] != null && !emailIDArr[i].equals("") && info.getObject(emailIDArr[i]) != null) {
                            franchiseeNo = (ArrayList) info.getObject(emailIDArr[i]);
                            if (!franchiseeNo.contains(result.getString("FRANCHISEE_NO")))
                                franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        } else if (emailIDArr[i] != null && !emailIDArr[i].equals("")) {
                            franchiseeNo = new ArrayList();
                            franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        }
                    }
                }

                emailIDArr = null;
                if (StringUtil.isValidNew(result.getString("STORE_EMAIL"))) {
                    emailIDArr = result.getString("STORE_EMAIL").replaceAll(";", ",").replaceAll(" ", "").split(",");
                }

                if (emailIDArr != null) {
                    for (int i = 0; i < emailIDArr.length; i++) {
                        if (emailIDArr[i] != null && !emailIDArr[i].equals("") && info.getObject(emailIDArr[i]) != null) {
                            franchiseeNo = (ArrayList) info.getObject(emailIDArr[i]);
                            if (!franchiseeNo.contains(result.getString("FRANCHISEE_NO")))
                                franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        } else if (emailIDArr[i] != null && !emailIDArr[i].equals("")) {
                            franchiseeNo = new ArrayList();
                            franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        }
                    }
                }

                emailIDArr = null;
                if (StringUtil.isValidNew(result.getString("LEGAL_NOTICE_EMAIL"))) {
                    emailIDArr = result.getString("LEGAL_NOTICE_EMAIL").replaceAll(";", ",").replaceAll(" ", "").split(",");
                }

                if (emailIDArr != null) {
                    for (int i = 0; i < emailIDArr.length; i++) {
                        if (emailIDArr[i] != null && !emailIDArr[i].equals("") && info.getObject(emailIDArr[i]) != null) {
                            franchiseeNo = (ArrayList) info.getObject(emailIDArr[i]);
                            if (!franchiseeNo.contains(result.getString("FRANCHISEE_NO")))
                                franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        } else if (emailIDArr[i] != null && !emailIDArr[i].equals("")) {
                            franchiseeNo = new ArrayList();
                            franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        }
                    }
                }
            }//end while
            pstmt = con.prepareStatement("SELECT DISTINCT FU.FRANCHISEE_NO,FU.EMAIL_ID FROM FRANCHISEE_USERS FU , USERS U WHERE  U.FRANCHISEE_NO=FU.FRANCHISEE_NO AND  U.STATUS=1 AND U.IS_DELETED='N' AND FU.STATUS=1 AND FU.EMAIL_ID IS NOT NULL AND FU.EMAIL_ID !=''");
            result = pstmt.executeQuery();
            while (result.next()) {
                //If more Franchisee have same Mail Ids
                emailIDArr = null;
                if (StringUtil.isValidNew(result.getString("EMAIL_ID"))) {
                    emailIDArr = result.getString("EMAIL_ID").replaceAll(";", ",").replaceAll(" ", "").split(",");
                }

                if (emailIDArr != null) {
                    for (int i = 0; i < emailIDArr.length; i++) {
                        if (emailIDArr[i] != null && !emailIDArr[i].equals("") && info.getObject(emailIDArr[i]) != null) {
                            franchiseeNo = (ArrayList) info.getObject(emailIDArr[i]);
                            if (!franchiseeNo.contains(result.getString("FRANCHISEE_NO")))
                                franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        } else if (emailIDArr[i] != null && !emailIDArr[i].equals("")) {
                            franchiseeNo = new ArrayList();
                            franchiseeNo.add(result.getString("FRANCHISEE_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        }
                    }
                }
            }//end while
            // Lender tab removed
            //pstmt  = con.prepareStatement("SELECT ADDR.EMAIL_IDS,FO.FRANCHISEE_NO AS FRAN_NO FROM ADDRESS ADDR LEFT JOIN FIM_OWNERS FO ON FO.FRANCHISE_OWNER_ID=ADDR.FOREIGN_ID WHERE  ADDR.FOREIGN_TYPE='fimOwners' AND ADDR.EMAIL_IDS IS NOT NULL AND ADDR.EMAIL_IDS<>'' UNION SELECT ADDR.EMAIL_IDS,FL.ENTITY_ID AS FRAN_NO FROM ADDRESS ADDR LEFT JOIN FIM_LENDER FL ON FL.LENDER_ID=ADDR.FOREIGN_ID WHERE  ADDR.FOREIGN_TYPE='fimLender' AND ADDR.EMAIL_IDS IS NOT NULL AND ADDR.EMAIL_IDS<>'' UNION SELECT ADDR.EMAIL_IDS,FEL.ENTITY_ID AS FRAN_NO FROM ADDRESS ADDR LEFT JOIN FIM_ENTITY_DETAIL FEL ON FEL.FIM_ENTITY_ID=ADDR.FOREIGN_ID WHERE  ADDR.FOREIGN_TYPE='fimEntityDetail' AND ADDR.EMAIL_IDS IS NOT NULL AND ADDR.EMAIL_IDS<>'' UNION SELECT ADDR.EMAIL_IDS,FG.ENTITY_ID AS FRAN_NO FROM ADDRESS ADDR LEFT JOIN FIM_GUARANTOR FG ON FG.GUARANTOR_ID=ADDR.FOREIGN_ID WHERE  ADDR.FOREIGN_TYPE='fimGuarantor' AND ADDR.EMAIL_IDS IS NOT NULL AND ADDR.EMAIL_IDS<>''");
            // Now Owners mail ids are not going to ADDRESS table,
            pstmt = con.prepareStatement("SELECT FO.EMAIL AS EMAIL_IDS, OWN.FRANCHISEE_NO AS FRAN_NO  FROM OWNERS OWN,FIM_OWNERS FO WHERE   FO.EMAIL IS NOT NULL AND FO.EMAIL<>'' AND OWN.FRANCHISEE_NO IS NOT NULL AND OWN.OWNER_ID=FO.FRANCHISE_OWNER_ID UNION SELECT ADDR.EMAIL_IDS,FEL.ENTITY_ID AS FRAN_NO FROM ADDRESS ADDR LEFT JOIN FIM_ENTITY_DETAIL FEL ON FEL.FIM_ENTITY_ID=ADDR.FOREIGN_ID WHERE  ADDR.FOREIGN_TYPE='fimEntityDetail' AND ADDR.EMAIL_IDS IS NOT NULL AND ADDR.EMAIL_IDS<>'' AND FEL.ENTITY_ID IS NOT NULL");
            result = pstmt.executeQuery();
            while (result.next()) {
                //If more Franchisee have same Mail Ids
                emailIDArr = null;
                if (StringUtil.isValidNew(result.getString("EMAIL_IDS"))) {
                    emailIDArr = result.getString("EMAIL_IDS").replaceAll(";", ",").replaceAll(" ", "").split(",");
                }

                if (emailIDArr != null) {
                    for (int i = 0; i < emailIDArr.length; i++) {
                        if (emailIDArr[i] != null && !emailIDArr[i].equals("") && info.getObject(emailIDArr[i]) != null) {
                            franchiseeNo = (ArrayList) info.getObject(emailIDArr[i]);
                            if (!franchiseeNo.contains(result.getString("FRAN_NO")))
                                franchiseeNo.add(result.getString("FRAN_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        } else if (emailIDArr[i] != null && !emailIDArr[i].equals("")) {
                            franchiseeNo = new ArrayList();
                            franchiseeNo.add(result.getString("FRAN_NO"));
                            //Demo_ISSUE_7012 starts
                            info.set(emailIDArr[i].toLowerCase(), franchiseeNo);
                            //Demo_ISSUE_7012 ends
                        }
                    }
                }
            }//end while


        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
        return info;
    }

    // Saving the Attachment of Mails
    public void saveFile(String fileName, InputStream inputStream) {
        int data;
        try {
            OutputStream outputStream = new FileOutputStream(fileName);
            while ((data = inputStream.read()) != -1) {
                outputStream.write(data);
            }
            inputStream.close();
            outputStream.close();
        } catch (IOException ex) {
            logger.info(ex.getMessage());
        }
    }

    public static void moveFile(File file, String basePath, String fileName) {
        File newFile = new File(basePath + fileName);
        try {
            FileInputStream fis = new FileInputStream(file);
            FileOutputStream fos = new FileOutputStream(newFile);
            int availableBytes = fis.available();
            for (int count = 1; count <= availableBytes; count++) {
                int readByte = fis.read();
                fos.write(readByte);
            }
            fis.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}//End Of Class





