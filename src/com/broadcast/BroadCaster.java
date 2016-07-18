//*************************************************************************************
//*********************************************************************************** *
//author Aritra Dhar 																* *
//Research Engineer																  	* *
//Xerox Research Center India													    * *
//Bangalore, India																    * *
//--------------------------------------------------------------------------------- * * 
///////////////////////////////////////////////// 									* *
//The program will do the following:::: // 											* *
///////////////////////////////////////////////// 									* *
//version 1.0 																		* *
//*********************************************************************************** *
//*************************************************************************************


package com.broadcast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import sun.security.ec.SunEC;

public class BroadCaster 
{
	int counter;
	String imageDBPath;
	//file name and AES key in bytes
	HashMap<String, byte[]> imageKeyMap;
	//filename and encryption of the file in bytes
	HashMap<String, byte[]> encImgMap;
	//filename and encryption of the file in Base64 String mode
	HashMap<String, String> encImgBase64Map;
	//file name and key in Base64 String mode
	HashMap<String, String> imageKeyBase64Map;
	//file name and the digital signature in Base64 String mode
	HashMap<String, String> imageSignatureBase64Map;
	
	PublicKey ec_pk;
	PrivateKey ec_sk;
	
	public BroadCaster(String imageDBpath) throws NoSuchAlgorithmException, NoSuchProviderException, IOException
	{
		this.imageDBPath = imageDBpath;
		
		File file = new File(imageDBpath);
		if(!file.exists())
			throw new RuntimeException("image DB path not found");
		
		this.counter = file.listFiles().length;
		imageKeyMap = new HashMap<String, byte[]>();
		imageKeyBase64Map = new HashMap<String, String>();
		encImgMap = new HashMap<String, byte[]>();
		encImgBase64Map = new HashMap<String, String>();
		imageSignatureBase64Map = new HashMap<String, String>();
		
		this.makeDSKeyPair(384);
	}
	
	/*
	 * For using encryption and decryption methods 
	 * independently
	 */
	public BroadCaster()
	{
		imageKeyMap = new HashMap<String, byte[]>();
		imageKeyBase64Map = new HashMap<String, String>();
		encImgMap = new HashMap<String, byte[]>();
		encImgBase64Map = new HashMap<String, String>();
		imageSignatureBase64Map = new HashMap<String, String>();
	}
	
	public void makeDSKeyPair(int securityParameter) throws NoSuchAlgorithmException, NoSuchProviderException, IOException
	{
		Provider sunEC = new SunEC();
        Security.addProvider(sunEC);
		KeyPairGenerator kg = KeyPairGenerator.getInstance("EC", "SunEC");
		kg.initialize(securityParameter, new SecureRandom());
		KeyPair kp = kg.genKeyPair();
		this.ec_pk = kp.getPublic();
		this.ec_sk = kp.getPrivate();
		
		FileWriter fw1 = new FileWriter("C:\\KeyBase\\EC_PK.key");
		FileWriter fw2 = new FileWriter("C:\\KeyBase\\EC_SK.key");
		fw1.append(Base64.encodeBase64URLSafeString(this.ec_pk.getEncoded()));
		fw2.append(Base64.encodeBase64URLSafeString(this.ec_sk.getEncoded()));
		
		fw1.close();
		fw2.close();
	}
	
	public void generateKey() throws NoSuchAlgorithmException, IOException
	{
		File file = new File(this.imageDBPath);
		File[] files = file.listFiles();
		
		for(int i = 0; i< this.counter; i++)
		{
			KeyGenerator kg = KeyGenerator.getInstance("AES");
			kg.init(256);
			SecretKey sec = kg.generateKey();		
			byte[] keyByte = sec.getEncoded();
			this.imageKeyMap.put(files[i].getName(), keyByte);
			this.imageKeyBase64Map.put(files[i].getName(), Base64.encodeBase64URLSafeString(keyByte));
		}
		
		//System.out.println(imageKeyBase64Map.entrySet());
	}
	
	//encrypt and generate signature
	
	public void encrypt() throws NoSuchAlgorithmException, NoSuchPaddingException, 
								 InvalidKeyException, IOException, 
								 IllegalBlockSizeException, BadPaddingException, 
								 InvalidParameterSpecException, InvalidAlgorithmParameterException, 
								 NoSuchProviderException, SignatureException
	{
		Iterator<String> it = imageKeyMap.keySet().iterator();		
		Cipher ci = Cipher.getInstance("AES/CBC/PKCS5Padding");
		
		while(it.hasNext())
		{			
			String filename = it.next();
			byte[] key = imageKeyMap.get(filename);
			
			File oldFile = new File("C://ImageDB//"+ filename);
			byte[] fileBytes = Files.readAllBytes(oldFile.toPath());
			
			SecretKey sec = new SecretKeySpec(key, "AES");
			SecureRandom rand = new SecureRandom();
			byte[] iv = new byte[16];
			rand.nextBytes(iv);
			
			ci.init(Cipher.ENCRYPT_MODE, sec, new IvParameterSpec(iv));
			
			byte[] cipherText = ci.doFinal(fileBytes);
			byte[] ivAttachedCipherText = new byte[cipherText.length + 16];
			
			System.arraycopy(cipherText, 0, ivAttachedCipherText, 0, cipherText.length);
			System.arraycopy(iv, 0, ivAttachedCipherText, cipherText.length, iv.length);
			
			encImgMap.put(filename, ivAttachedCipherText);
			encImgBase64Map.put(filename, Base64.encodeBase64URLSafeString(ivAttachedCipherText));
			
			//signature generation
			Signature sig = Signature.getInstance("SHA256withECDSA", "SunEC");
			sig.initSign(this.ec_sk);
			sig.update(ivAttachedCipherText);
			byte[] signature = sig.sign();
			imageSignatureBase64Map.put(filename, Base64.encodeBase64URLSafeString(signature));
			
			//System.out.println(ivAttachedCipherText.length);
			
			OutputStream out = null;
			try 
			{
			    out = new BufferedOutputStream(new FileOutputStream("C://EncImageDB//"+ filename));
			    out.write(ivAttachedCipherText);
			} 
			finally 
			{
			    if (out != null)
			    	out.close();
			}
		}
	}
	
	public void decrypt() throws NoSuchAlgorithmException, NoSuchPaddingException, 
								 InvalidKeyException, InvalidAlgorithmParameterException, 
								 IllegalBlockSizeException, BadPaddingException, IOException
	{
		Iterator<String> it = imageKeyMap.keySet().iterator();
		Cipher ci = Cipher.getInstance("AES/CBC/PKCS5Padding");
		while(it.hasNext())
		{
			String filename = it.next();
			byte[] key = Base64.decodeBase64(imageKeyBase64Map.get(filename));
			
			SecretKey sec = new SecretKeySpec(key, "AES");
			
			//byte[] ivAttachedCipherText = encImgMap.get(filename);
			byte[] ivAttachedCipherText = Base64.decodeBase64(encImgBase64Map.get(filename));
			
			byte[] cipherText = Arrays.copyOf(ivAttachedCipherText, ivAttachedCipherText.length - 16);
			byte[] iv = Arrays.copyOfRange(ivAttachedCipherText, ivAttachedCipherText.length - 16, ivAttachedCipherText.length);
			
			ci.init(Cipher.DECRYPT_MODE, sec, new IvParameterSpec(iv));
			byte[] plaintext = ci.doFinal(cipherText);
			
			OutputStream out = null;
			try 
			{
			    out = new BufferedOutputStream(new FileOutputStream("C://DecImageDB//"+ filename));
			    out.write(plaintext);
			} 
			finally 
			{
			    if (out != null)
			    	out.close();
			}
		}
	}
	
	public byte[] Decrypt(String keyString, String data) throws NoSuchAlgorithmException, NoSuchPaddingException, 
															    InvalidKeyException, InvalidAlgorithmParameterException, 
															    IllegalBlockSizeException, BadPaddingException
	{
		byte[] ivAttachedCipherText = Base64.decodeBase64(data);
		byte[] cipherText = Arrays.copyOf(ivAttachedCipherText, ivAttachedCipherText.length - 16);
		byte[] iv = Arrays.copyOfRange(ivAttachedCipherText, ivAttachedCipherText.length - 16, ivAttachedCipherText.length);
		
		byte[] keyByte = Base64.decodeBase64(keyString);
		Cipher ci = Cipher.getInstance("AES/CBC/PKCS5Padding");
		
		SecretKey sec = new SecretKeySpec(keyByte, "AES");
		
		ci.init(Cipher.DECRYPT_MODE, sec, new IvParameterSpec(iv));
		byte[] plaintext = ci.doFinal(cipherText);
			
		return plaintext;
	}
	
	public JSONObject JsonImageBase64KeyBuilder() throws IOException
	{
		/*
		 * JSOn key storage
		 */
		//jsonObject for img-key map
		JSONObject JSON_IMG_KEY_BASE64 = new JSONObject();
		JSONArray jArray = new JSONArray();
		
		Iterator<String> it =  imageKeyBase64Map.keySet().iterator();
		
		for(int i = 0; i< imageKeyBase64Map.keySet().size(); i++)
		{
			JSONObject jObject = new JSONObject();
			
			String fileName = "autogen_"+i+".jpg";
			String base64Key = imageKeyBase64Map.get(fileName);
			jObject.put("imgFile", fileName);
			jObject.put("key", base64Key);
						
			jArray.put(jObject);
			
		}
		JSON_IMG_KEY_BASE64.put("ImageBase64KeyMap", jArray);
		
		//System.out.println(JSON_IMG_KEY_BASE64.toString(2));
		
		FileWriter fw = new FileWriter("C:\\KeyBase\\Key.json");
		fw.append(JSON_IMG_KEY_BASE64.toString(2));
		fw.close();
		
		return JSON_IMG_KEY_BASE64;
	}
	
	
	public JSONObject[] JsonEncryptedImgBase64Builder() throws IOException
	{
		/*
		 * Json encrypted cipher text storage
		 */
		JSONObject[] JSON_IMG_ENC_BASE64 = new JSONObject[encImgBase64Map.keySet().size()];
		
		int i = 0;
		Iterator<String> it = encImgBase64Map.keySet().iterator();
		
		while(it.hasNext())
		{
			JSON_IMG_ENC_BASE64[i] = new JSONObject();
			
			String fileName = it.next();
			String base64CipherText = encImgBase64Map.get(fileName);
			String signature = imageSignatureBase64Map.get(fileName);
			
			JSON_IMG_ENC_BASE64[i].put("imgFile", fileName);
			JSON_IMG_ENC_BASE64[i].put("cipherText", base64CipherText);
			JSON_IMG_ENC_BASE64[i].put("signature", signature);
			
			FileWriter fw = new FileWriter("C:\\Users\\w4j3yyfd\\workspace\\Crypt\\CipherBin\\"+ fileName + "_cipherText.json");
			fw.append(JSON_IMG_ENC_BASE64[i].toString(2));
			fw.close();
			i++;
		}
		
		return JSON_IMG_ENC_BASE64;
	}
	/*
	 * test
	 */
	public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, 
												  InvalidKeyException, IOException, IllegalBlockSizeException, 
												  BadPaddingException, InvalidParameterSpecException, 
												  InvalidAlgorithmParameterException, NoSuchProviderException, SignatureException 
	{
		BroadCaster skg = new BroadCaster("C:\\ImageDB");
		skg.generateKey();
		skg.encrypt();
		System.out.println("Encryption Done");
		skg.decrypt();
		System.out.println("Decryption Done");
		skg.JsonImageBase64KeyBuilder();
		skg.JsonEncryptedImgBase64Builder();
		
		System.out.println("Done..");
	}
	
}
