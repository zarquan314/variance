package zero_knowledge_proofs;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.Random;

import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import zero_knowledge_proofs.CryptoData.BigIntData;
import zero_knowledge_proofs.CryptoData.CryptoData;
import zero_knowledge_proofs.CryptoData.CryptoDataArray;
import zero_knowledge_proofs.CryptoData.ECCurveData;
import zero_knowledge_proofs.CryptoData.ECPointData;

abstract public class VarianceToolkit {
	public static ZKPProtocol tableEqualityProver;
	public static ZKPProtocol consistantTableEncryptionProver;
	
	private static ZKPProtocol bitCommitmentEqualityProtocol;
	
	private static boolean registered = false;
	private static int[][] nChooseKTable = {{1}};
	
	public static int choose(int n, int k)
	{
		n++;
		if(n < k || k < 0 || n <= 0) throw new ArrayIndexOutOfBoundsException();
		int reducedK = Math.min(k, n-(k+1));
		if(n > nChooseKTable.length)
		{
			int[][] temp = new int[n][];
			for(int i = 0; i < nChooseKTable.length; i++)
			{
				temp[i] = nChooseKTable[i];
			}
			nChooseKTable = temp;
		}
		if(nChooseKTable[n-1] == null)
		{
			int reducedN = (n+1)/2;
			nChooseKTable[n-1] = new int[reducedN];
		}
		return rChoose(n, reducedK);
	}
	
	private static int rChoose(int n, int k)
	{
		if(k == 0) return 1;
		if(nChooseKTable[n-1][k-1] == 0)
		{
			nChooseKTable[n-1][k-1] = (rChoose(n, k-1) * (n-k))/(k);
		}
		return nChooseKTable[n-1][k-1];
	}
	public static boolean register()
	{
		if(registered)
			return true;
		registered = true;
		if(!ZKToolkit.registered())
		{
			ZKToolkit.register();
		}

		try {
			tableEqualityProver = ZKPProtocol.generateProver("AND(OR(AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs),AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs),AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs)),OR(AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs),AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs),AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs)),OR(AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs),AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs),AND(ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs,ECEqualLogs)))");
			consistantTableEncryptionProver = ZKPProtocol.generateProver("OR(AND(ECSchnorr,ECEqualLogs,ECEqualLogs,ECSchnorr,ECSchnorr),AND(ECSchnorr,ECEqualLogs,ECEqualLogs,ECSchnorr,ECSchnorr))");
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| SecurityException | InvalidStringFormatException e) {
			e.printStackTrace();
		}	
		return true;
	}
	
	
	
	public static ECPedersenOwnedBitwiseCommitment ecConvertToBits(BigInteger m, BigInteger key, int numBits, CryptoData env, Random r) throws InputMismatchException
	{
		if(numBits == 0) numBits = m.bitLength();
		if(numBits < m.bitLength()) throw new InputMismatchException ("Need " + m.bitLength() + " bits.  Given " + numBits);
		CryptoData[] unpackedEnv = env.getCryptoDataArray();
		ECCurve curve = unpackedEnv[0].getECCurveData();
		BigInteger order = curve.getOrder();
		int orderBits = order.bitLength();
		
		ECPedersenOwnedBitwiseCommitment toReturn = new ECPedersenOwnedBitwiseCommitment();
		
		toReturn.comm = new ECPedersenCommitment[numBits];
		toReturn.keys = new BigInteger[numBits];
		
		BigInteger pcKey;
		
		toReturn.m = m;
		for(int i = 1; i < numBits; i++)
		{
			do
			{
				pcKey = new BigInteger(orderBits, r);
			}while(pcKey.compareTo(order) >= 0);
			key = key.subtract(pcKey.shiftLeft(i));
			if(m.testBit(i))
			{
				toReturn.comm[i] = new ECPedersenCommitment(BigInteger.ONE, pcKey, env);
				toReturn.keys[i] = pcKey;
			}
			else
			{

				toReturn.comm[i] = new ECPedersenCommitment(BigInteger.ZERO, pcKey, env);
				toReturn.keys[i] = pcKey;
			}
		}

		if(m.testBit(0))
		{
			toReturn.comm[0] = new ECPedersenCommitment(BigInteger.ONE, key, env);
			toReturn.keys[0] = key;
		}
		else
		{

			toReturn.comm[0] = new ECPedersenCommitment(BigInteger.ZERO, key, env);
			toReturn.keys[0] = key;
		}
		return toReturn;
	}
	public static boolean checkBitCommitment(ECPedersenCommitment total, ECPedersenCommitment[] bits, CryptoData env) {
		ECPedersenCommitment current = bits[bits.length-1];
		for(int i = bits.length-2; i >= 0; i--)
		{
			current = bits[i].multiplyShiftedCommitment(current, 1, env);
		}
		return (current.getCommitment(env).equals(total.getCommitment(env)));
	}
	
	
	public static CryptoData[][] shuffleTable(CryptoData[][] origTable, BigInteger[][] keyChanges, int[] shuffleRows, CryptoData environment)
	{
		//TODO:  Error Checking
		CryptoData[][] toReturn = new CryptoData[3][5];
		for(int i = 0; i < 3; i++)
		{
			for(int j = 0; j < 5; j++)
			{
				toReturn[shuffleRows[i]][j] = ZKToolkit.randomizeEllipticElgamal(origTable[i][j], keyChanges[i][j], environment);
			}
		}
		
		
		return toReturn;
	}
	
	public static CryptoData createZeroKnowledgeProverInputsForShuffle(CryptoData[][] origTable, CryptoData[][] newTable, BigInteger[][] keyChanges, int[] shuffle, CryptoData environment, SecureRandom r)
	{
		CryptoData[] e = environment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		BigInteger order = c.getOrder();
		int bits = order.bitLength();
		
		CryptoData[] layer1 = new CryptoData[3];
		
		CryptoData[][] entries = new CryptoData[5][];
		for(int i = 0; i < 3; i++)
		{

			CryptoData[] origRow = origTable[i];
			CryptoData[] layer2 = new CryptoData[4];

			BigInteger[] challenges = new BigInteger[3];
			
			for(int j = 0; j < 5; j++)
			{

				entries[j] = origRow[j].getCryptoDataArray();
			}
			for(int j = 0; j < 3; j++)
			{
				CryptoData[] newRow = newTable[j];
				
				CryptoData[] layer3 = new CryptoData[5];
				
				if(shuffle[i] == j)
				{
					challenges[j] = BigInteger.ZERO;
				}
				else
				{
					challenges[j] = new BigInteger(bits-1, r);
				}
				for(int k = 0; k < 5; k++)
				{
					CryptoData[] newEntry = newRow[k].getCryptoDataArray();
					ECPoint origCipher = entries[k][0].getECPointData(c);
					ECPoint origCipherKey = entries[k][1].getECPointData(c);
					ECPoint newCipher = newEntry[0].getECPointData(c);
					ECPoint newCipherKey = newEntry[1].getECPointData(c);
					ECPoint cipherDiff = newCipher.subtract(origCipher);
					ECPoint cipherKeyDiff = newCipherKey.subtract(origCipherKey);
					CryptoData[] cell;
					BigInteger temp = new BigInteger(bits, r);
					while(temp.compareTo(order) >= 0) 
						temp = new BigInteger(bits, r);
					if(shuffle[i] == j)
					{
						//input = [y_g, y_h, r, x]
						cell = new CryptoData[4];

						BigInteger temp2 = keyChanges[i][k];
						cell[3] = new BigIntData(temp2);
					}
					else
					{
						//input = [y_g, y_h, z]
						cell = new CryptoData[3];
					}
					cell[0] = new ECPointData(cipherDiff);
					cell[1] = new ECPointData(cipherKeyDiff);
					cell[2] = new BigIntData(temp);
					layer3[k] = new CryptoDataArray(cell);
				}
				layer2[j] = new CryptoDataArray(layer3);					
			}
			layer2[3] = new CryptoDataArray(challenges);
			layer1[i] = new CryptoDataArray(layer2);
		}
		return new CryptoDataArray(layer1);
	}
	public static CryptoData createZeroKnowledgeVerifierInputsForShuffle(CryptoData[][] origTable, CryptoData[][] newTable, CryptoData environment)
	{

		CryptoData[] e = environment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		CryptoData[] layer1 = new CryptoData[3];
		
		for(int i = 0; i < 3; i++)
		{

			CryptoData[] origRow = origTable[i];
			CryptoData[] layer2 = new CryptoData[3];

			
			for(int j = 0; j < 3; j++)
			{
				CryptoData[] newRow = newTable[j];
				
				CryptoData[] layer3 = new CryptoData[5];
				
				for(int k = 0; k < 5; k++)
				{	
					CryptoData[] origEntry = origRow[k].getCryptoDataArray();
					ECPoint origEntryM = origEntry[0].getECPointData(c);
					ECPoint origEntryKey = origEntry[1].getECPointData(c);
					CryptoData[] newEntry = newRow[k].getCryptoDataArray();
					ECPoint newEntryM = newEntry[0].getECPointData(c);
					ECPoint newEntryKey = newEntry[1].getECPointData(c);
					CryptoData diffM = new ECPointData(newEntryM.subtract(origEntryM));
					CryptoData diffKey = new ECPointData(newEntryKey.subtract(origEntryKey));
					layer3[k] = new CryptoDataArray(new CryptoData[]{diffM, diffKey});
				}
				layer2[j] = new CryptoDataArray(layer3);
			}
			layer1[i] = new CryptoDataArray(layer2);
		}
		return new CryptoDataArray(layer1);
	}
//		CryptoData[] layer1 = new CryptoData[3];
//		
//		for(int i = 0; i < 3; i++)
//		{
//
//			CryptoData[] layer2 = new CryptoData[5];
//			for(int j = 0; j < 5; j++)
//			{
//				CryptoData[] origEntry = origTable[i][j].getCryptoDataArray();
//				ECPoint origCipher = origEntry[0].getECPointData(c);
//				ECPoint origCipherKey = origEntry[1].getECPointData(c);
//
//				CryptoData[] layer3 = new CryptoData[3];
//				for(int k = 0; k < 3; k++)
//				{
//					CryptoData[] newEntry = origTable[k][j].getCryptoDataArray();
//					ECPoint cipherDiff = origCipher.subtract(newEntry[0].getECPointData(c));
//					ECPoint cipherKeyDiff = origCipherKey.subtract(newEntry[1].getECPointData(c));
//					CryptoData[] cell;
//					cell = new CryptoData[3];
//					cell[0] = new ECPointData(cipherKeyDiff);
//					cell[1] = new ECPointData(cipherDiff);
//					layer3[k] = new CryptoDataArray(cell);
//				}
//				layer2[j] = new CryptoDataArray(layer3);					
//			}
//
//			layer1[i] = new CryptoDataArray(layer2);
//		}
//		return new CryptoDataArray(layer1);
//	}
	
	public static CryptoData[][] getBasicTable(CryptoData environment)
	{
		CryptoData[] e = environment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		BigInteger order = c.getOrder();
		ECPoint g = e[0].getECPointData(c);
		BigInteger twoInverse = BigInteger.valueOf(2).modInverse(order);
		ECPoint _half = g.multiply(twoInverse);
		ECPoint _quarter = _half.multiply(twoInverse);
		
		
		ECPointData inf = new ECPointData(c.getInfinity());
		CryptoData half = new CryptoDataArray(new CryptoData[] {new ECPointData(_half), inf});
		CryptoData quarter = new CryptoDataArray(new CryptoData[] {new ECPointData(_quarter), inf});
		CryptoData negHalf = new CryptoDataArray(new CryptoData[] {new ECPointData(_half.negate()), inf});
		CryptoData negQuarter = new CryptoDataArray(new CryptoData[] {new ECPointData(_quarter.negate()), inf});
		CryptoData one = new CryptoDataArray(new CryptoData[] {new ECPointData(g), inf});
		CryptoData negOne = new CryptoDataArray(new CryptoData[] {new ECPointData(g.negate()), inf});
		CryptoData zero = new CryptoDataArray(new CryptoData[] {inf, inf});
		
		CryptoData[] lt = new CryptoData[] {negOne, negQuarter, negQuarter, negQuarter, negQuarter};
		CryptoData[] eq = new CryptoData[] {zero, zero, negHalf, half, zero};
		CryptoData[] gt = new CryptoData[] {one, quarter, quarter, quarter, quarter};

		return new CryptoData[][] {lt,eq,gt};
	}

	public static CryptoData[] createTableCommitments(CryptoData[] row, BigInteger[] keys, boolean bit, boolean isHost, CryptoData environment)
	{
		CryptoData[] toReturn = new CryptoData[4];
		
		
		if(isHost)
		{
			if(bit)
			{	//bit is 1
				toReturn[0] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[0], environment);
				toReturn[1] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[1], environment);
				
				toReturn[2] = ZKToolkit.randomizeEllipticElgamal(row[3], keys[2], environment);
				toReturn[3] = ZKToolkit.randomizeEllipticElgamal(row[4], keys[3], environment);
			}
			else
			{	//bit is 0
				toReturn[0] = ZKToolkit.randomizeEllipticElgamal(row[1], keys[0], environment);
				toReturn[1] =  ZKToolkit.randomizeEllipticElgamal(row[2], keys[1], environment);
				
				toReturn[2] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[2], environment);
				toReturn[3] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[3], environment);
			}
		}
		else
		{//00 01 10 11
			if(bit)
			{	//bit is 1
				toReturn[0] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[0], environment);
				toReturn[2] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[2], environment);
				
				toReturn[1] = ZKToolkit.randomizeEllipticElgamal(row[2], keys[1], environment);
				toReturn[3] =  ZKToolkit.randomizeEllipticElgamal(row[4], keys[3], environment);
			}
			else
			{	//bit is 0
				toReturn[0] = ZKToolkit.randomizeEllipticElgamal(row[1], keys[0], environment);
				toReturn[2] =  ZKToolkit.randomizeEllipticElgamal(row[3], keys[2], environment);


				toReturn[1] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[1], environment);
				toReturn[3] = ZKToolkit.ellipticExpElgamalEncrypt(BigInteger.ZERO, keys[3], environment);
			}
		}
		return toReturn;
	}
	
	public static CryptoData getTableCoorespondenceProverData(CryptoData[] row, CryptoData[] encryptions, BigInteger[] keys, CryptoData bitComm, BigInteger bitCommKey, boolean bit, boolean isHost, CryptoData environment, SecureRandom r)
	{
		CryptoData[] e = environment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		BigInteger order = c.getOrder();
		int bits = order.bitLength();
		CryptoData[] inputs0 = new CryptoData[5];
		CryptoData[] inputs1 = new CryptoData[5];
		CryptoData[] inputs2 = new CryptoData[2];
		BigInteger[] randoms = new BigInteger[10];
		for(int i = 0; i < 10; i++)
		{
			randoms[i] = new BigInteger(bits, r);
			while(randoms[i].compareTo(order) >= 0)
			{
				randoms[i] = new BigInteger(bits, r);
			}
		}
		CryptoData[][] rowUnpacked = new CryptoData[row.length][];
		for(int i = 0; i < row.length; i++)
		{
			rowUnpacked[i] = row[i].getCryptoDataArray();
		}
		BigInteger fakeChallenge = new BigInteger(bits - 1, r);
		if(bit)
		{	 //00 01 10 11  bit is 1
			//using ECEqualDiscreteLog 
			inputs2[0] = new BigIntData(fakeChallenge);
			inputs2[1] = new BigIntData(BigInteger.ZERO);
			inputs0[0] = new CryptoDataArray(new CryptoData[] {new ECPointData(bitComm.getECPointData(c)), new BigIntData(randoms[0])});
			inputs1[0] = new CryptoDataArray(new CryptoData[] {new ECPointData(bitComm.getECPointData(c).subtract(g)), new BigIntData(randoms[5]), new BigIntData(bitCommKey)});

			CryptoData[] enc0 = encryptions[0].getCryptoDataArray();
			CryptoData[] enc1 = encryptions[1].getCryptoDataArray();
			CryptoData[] enc2 = encryptions[2].getCryptoDataArray();
			CryptoData[] enc3 = encryptions[3].getCryptoDataArray();
			if(!isHost)
			{	
				inputs0[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c).subtract(rowUnpacked[1][0].getECPointData(c))), new ECPointData(enc0[1].getECPointData(c).subtract(rowUnpacked[1][1].getECPointData(c))), new BigIntData(randoms[1])});
				inputs0[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c).subtract(rowUnpacked[2][0].getECPointData(c))), new ECPointData(enc1[1].getECPointData(c).subtract(rowUnpacked[2][1].getECPointData(c))), new BigIntData(randoms[2])});
				inputs0[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c)), new BigIntData(randoms[3])});
				inputs0[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c)), new BigIntData(randoms[4])});
				
				inputs1[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c).subtract(rowUnpacked[3][0].getECPointData(c))), new ECPointData(enc2[1].getECPointData(c).subtract(rowUnpacked[3][1].getECPointData(c))), new BigIntData(randoms[8]), new BigIntData(keys[2])});
				inputs1[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c).subtract(rowUnpacked[4][0].getECPointData(c))), new ECPointData(enc3[1].getECPointData(c).subtract(rowUnpacked[4][1].getECPointData(c))), new BigIntData(randoms[9]), new BigIntData(keys[3])});
				inputs1[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c)), new BigIntData(randoms[6]), new BigIntData(keys[0])});
				inputs1[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c)), new BigIntData(randoms[7]), new BigIntData(keys[1])});
			
			}
			else	
			{	
				inputs0[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c).subtract(rowUnpacked[1][0].getECPointData(c))), new ECPointData(enc0[1].getECPointData(c).subtract(rowUnpacked[1][1].getECPointData(c))), new BigIntData(randoms[1])});
				inputs0[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c).subtract(rowUnpacked[3][0].getECPointData(c))), new ECPointData(enc2[1].getECPointData(c).subtract(rowUnpacked[3][1].getECPointData(c))), new BigIntData(randoms[2])});
				inputs0[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c)), new BigIntData(randoms[3])});
				inputs0[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c)), new BigIntData(randoms[4])});
				
				inputs1[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c).subtract(rowUnpacked[2][0].getECPointData(c))), new ECPointData(enc1[1].getECPointData(c).subtract(rowUnpacked[2][1].getECPointData(c))), new BigIntData(randoms[8]), new BigIntData(keys[1])});
				inputs1[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c).subtract(rowUnpacked[4][0].getECPointData(c))), new ECPointData(enc3[1].getECPointData(c).subtract(rowUnpacked[4][1].getECPointData(c))), new BigIntData(randoms[9]), new BigIntData(keys[3])});
				inputs1[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c)), new BigIntData(randoms[6]), new BigIntData(keys[0])});
				inputs1[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c)), new BigIntData(randoms[7]), new BigIntData(keys[2])});
			}
		}
		else
		{
			inputs2[1] = new BigIntData(fakeChallenge);
			inputs2[0] = new BigIntData(BigInteger.ZERO);
			inputs0[0] = new CryptoDataArray(new CryptoData[] {new ECPointData(bitComm.getECPointData(c)), new BigIntData(randoms[0]), new BigIntData(bitCommKey)});
			inputs1[0] = new CryptoDataArray(new CryptoData[] {new ECPointData(bitComm.getECPointData(c).subtract(g)), new BigIntData(randoms[5])});
			CryptoData[] enc0 = encryptions[0].getCryptoDataArray();
			CryptoData[] enc1 = encryptions[1].getCryptoDataArray();
			CryptoData[] enc2 = encryptions[2].getCryptoDataArray();
			CryptoData[] enc3 = encryptions[3].getCryptoDataArray();
			if(!isHost)				
			{	
				inputs0[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c).subtract(rowUnpacked[1][0].getECPointData(c))), new ECPointData(enc0[1].getECPointData(c).subtract(rowUnpacked[1][1].getECPointData(c))), new BigIntData(randoms[1]), new BigIntData(keys[0])});
				inputs0[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c).subtract(rowUnpacked[2][0].getECPointData(c))), new ECPointData(enc1[1].getECPointData(c).subtract(rowUnpacked[2][1].getECPointData(c))), new BigIntData(randoms[2]), new BigIntData(keys[1])});
				inputs0[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c)), new BigIntData(randoms[3]), new BigIntData(keys[2])});
				inputs0[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c)), new BigIntData(randoms[4]), new BigIntData(keys[3])});
				
				inputs1[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c).subtract(rowUnpacked[3][0].getECPointData(c))), new ECPointData(enc2[1].getECPointData(c).subtract(rowUnpacked[3][1].getECPointData(c))), new BigIntData(randoms[8])});
				inputs1[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c).subtract(rowUnpacked[4][0].getECPointData(c))), new ECPointData(enc3[1].getECPointData(c).subtract(rowUnpacked[4][1].getECPointData(c))), new BigIntData(randoms[9])});
				inputs1[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c)), new BigIntData(randoms[6])});
				inputs1[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c)), new BigIntData(randoms[7])});
			
			}
			else	
			{	
				inputs0[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c).subtract(rowUnpacked[1][0].getECPointData(c))), new ECPointData(enc0[1].getECPointData(c).subtract(rowUnpacked[1][1].getECPointData(c))), new BigIntData(randoms[1]), new BigIntData(keys[0])});
				inputs0[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c).subtract(rowUnpacked[3][0].getECPointData(c))), new ECPointData(enc2[1].getECPointData(c).subtract(rowUnpacked[3][1].getECPointData(c))), new BigIntData(randoms[2]), new BigIntData(keys[2])});
				inputs0[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c)), new BigIntData(randoms[3]), new BigIntData(keys[1])});
				inputs0[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c)), new BigIntData(randoms[4]), new BigIntData(keys[3])});
				
				inputs1[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c).subtract(rowUnpacked[2][0].getECPointData(c))), new ECPointData(enc1[1].getECPointData(c).subtract(rowUnpacked[2][1].getECPointData(c))), new BigIntData(randoms[8])});
				inputs1[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c).subtract(rowUnpacked[4][0].getECPointData(c))), new ECPointData(enc3[1].getECPointData(c).subtract(rowUnpacked[4][1].getECPointData(c))), new BigIntData(randoms[9])});
				inputs1[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c)), new BigIntData(randoms[6])});
				inputs1[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c)), new BigIntData(randoms[7])});
			}
		}
		
		return new CryptoDataArray(new CryptoData[] {new CryptoDataArray (inputs0), new CryptoDataArray (inputs1), new CryptoDataArray (inputs2)});
	}
	public static CryptoData getTableCoorespondenceVerifierData(CryptoData[] row, CryptoData[] encryptions, CryptoData bitComm, boolean isHost, CryptoData environment)
	{
		CryptoData[] e = environment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		CryptoData[] inputs0 = new CryptoData[5];
		CryptoData[] inputs1 = new CryptoData[5];
		
		inputs0[0] = new CryptoDataArray(new CryptoData[] {new ECPointData(bitComm.getECPointData(c))});
		inputs1[0] = new CryptoDataArray(new CryptoData[] {new ECPointData(bitComm.getECPointData(c).subtract(g))});
		CryptoData[] enc0 = encryptions[0].getCryptoDataArray();
		CryptoData[] enc1 = encryptions[1].getCryptoDataArray();
		CryptoData[] enc2 = encryptions[2].getCryptoDataArray();
		CryptoData[] enc3 = encryptions[3].getCryptoDataArray();
		
		CryptoData[] row1 = row[1].getCryptoDataArray();
		CryptoData[] row2 = row[2].getCryptoDataArray();
		CryptoData[] row3 = row[3].getCryptoDataArray();
		CryptoData[] row4 = row[4].getCryptoDataArray();
		
		if(!isHost)
		{
			inputs0[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c).subtract(row1[0].getECPointData(c))), new ECPointData(enc0[1].getECPointData(c).subtract(row1[1].getECPointData(c)))});
			inputs0[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c).subtract(row2[0].getECPointData(c))), new ECPointData(enc1[1].getECPointData(c).subtract(row2[1].getECPointData(c)))});
			inputs0[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c))});
			inputs0[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c))});
			
			inputs1[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c).subtract(row3[0].getECPointData(c))), new ECPointData(enc2[1].getECPointData(c).subtract(row3[1].getECPointData(c)))});
			inputs1[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c).subtract(row4[0].getECPointData(c))), new ECPointData(enc3[1].getECPointData(c).subtract(row4[1].getECPointData(c)))});
			inputs1[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c))});
			inputs1[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c))});
		
		}
		else	
		{	
			inputs0[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c).subtract(row1[0].getECPointData(c))), new ECPointData(enc0[1].getECPointData(c).subtract(row1[1].getECPointData(c)))});
			inputs0[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c).subtract(row3[0].getECPointData(c))), new ECPointData(enc2[1].getECPointData(c).subtract(row3[1].getECPointData(c)))});
			inputs0[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c))});
			inputs0[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c))});
			
			inputs1[1] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc1[0].getECPointData(c).subtract(row2[0].getECPointData(c))), new ECPointData(enc1[1].getECPointData(c).subtract(row2[1].getECPointData(c)))});
			inputs1[2] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc3[0].getECPointData(c).subtract(row4[0].getECPointData(c))), new ECPointData(enc3[1].getECPointData(c).subtract(row4[1].getECPointData(c)))});
			inputs1[3] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc0[0].getECPointData(c))});
			inputs1[4] = new CryptoDataArray(new CryptoData[] {new ECPointData(enc2[0].getECPointData(c))});
		}
		return new CryptoDataArray(new CryptoData[] {new CryptoDataArray (inputs0), new CryptoDataArray (inputs1)});
	}
	
	public static CryptoData getTableProofEnvironment(CryptoData baseEnvironment)
	{
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		ECPoint h = e[1].getECPointData(c);
		CryptoData revE = new CryptoDataArray(new CryptoData[] {new ECCurveData(c, h), new ECPointData(g)});
		
		CryptoData half = new CryptoDataArray(new CryptoData[] {revE, revE, revE, revE, revE});
		return new CryptoDataArray(new CryptoData[] {half, half, baseEnvironment});
	}
	
	public static CryptoData getShuffleProofEnvironment(CryptoData baseEnvironment)
	{
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		ECPoint h = e[1].getECPointData(c);
		CryptoData revE = new CryptoDataArray(new CryptoData[] {new ECCurveData(c, h), new ECPointData(g)});
		
		CryptoData lowerLayer = new CryptoDataArray(new CryptoData[] {revE, revE, revE, revE, revE});
		CryptoData middleLayer = new CryptoDataArray(new CryptoData[] {lowerLayer, lowerLayer, lowerLayer});
		return new CryptoDataArray(new CryptoData[] {middleLayer, middleLayer, middleLayer,baseEnvironment});
	}
	
	public static ZKPProtocol createMultiSigProofNaive(int n, int k, ZKPProtocol keyProtocol)
	{
		
		if(n < k)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		
		 
		
		ZKPProtocol midLayer;
		if(k > 1)
		{
			ZKPProtocol[] andSubProtocol = new ZKPProtocol[k];
			for(int i = 0; i < k; i++)
			{
				andSubProtocol[i] = keyProtocol;
			}
			midLayer = new ZeroKnowledgeAndProver(andSubProtocol);
		}
		else midLayer = keyProtocol;
		if(n > k)
		{
			int nCrk = choose(n, k);
			ZKPProtocol[] orProtocol = new ZKPProtocol[nCrk];
			for(int i = 0; i < nCrk; i++)
			{
				orProtocol[i] = midLayer;
			}
			return new ZeroKnowledgeOrProver(orProtocol);
		}
		else return midLayer;
	}

	public static CryptoData createMultiSigEnvironmentNaive(int n, int k, CryptoData baseEnvironment)
	{
		if(n < k)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		if(n != 1)
		{
			CryptoData combined;
			if(k > 1)
			{
				CryptoData[] andSubEnvironment = new CryptoData[k];
				for(int i = 0; i < k; i++)
				{
					andSubEnvironment[i] = baseEnvironment;
				}
				combined = new CryptoDataArray(andSubEnvironment);
			}
			else combined = baseEnvironment;
			
			if(n > k)
			{
				int nCrk = choose(n, k);
				CryptoData[] orEnvironment = new CryptoData[nCrk];
				for(int i = 0; i < nCrk; i++)
				{
					orEnvironment[i] = combined;
				}
				return new CryptoDataArray(orEnvironment);
			}
			else
			{
				return combined;
			}
		}
		else return baseEnvironment;
	}
	public static CryptoData createMultiSigProverDataNaive(int n, int k, CryptoData[] publicKeys, CryptoData[] privateKeys, int[] positions, CryptoData baseEnvironment, ZKPProtocol p, BigInteger order, SecureRandom rand)  //TODO Should get order from baseEnvironment
	{
		if(n < k || publicKeys.length != n || privateKeys.length < k || privateKeys.length != positions.length)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		if(n != 1)
		{
			CryptoData[] simulatedChallenges;
			CryptoData[] orData;
			if(n > k)
			{
				int nCrk = choose(n, k);
				orData = new CryptoData[nCrk + 1];
				simulatedChallenges = new CryptoData[nCrk];
				int[] currentPositions = new int[k];
				for(int i = 0; i < k; i++)
				{
					currentPositions[i] = i;
				}
				for(int i = 0; i < nCrk; i++)
				{
					if(Arrays.equals(currentPositions, positions))
					{
						CryptoData combined;
						if(k > 1)
						{
							CryptoData[] andData = new CryptoData[k];
							for(int j = 0; j < k; j++)
							{
								if(privateKeys[j] == null)
									andData[j] = null;
								else
									andData[j] = createSchnorrProverInputsNoChecks(publicKeys[positions[j]], privateKeys[j], order, rand);
							}
							combined = new CryptoDataArray(andData);
						}
						else {
							if(privateKeys[0] == null)
								combined = null;
							else
								combined = createSchnorrProverInputsNoChecks(publicKeys[currentPositions[0]], privateKeys[0], order, rand);
						}
						orData[i] = combined;
						
						simulatedChallenges[i] = new BigIntData(BigInteger.ZERO);
					}
					else
					{
						CryptoData combined;
						if(k > 1)
						{
							CryptoData[] andData = new CryptoData[k];
							for(int j = 0; j < k; j++)
							{		//TODO: Find a good way to outsource simulation
								andData[j] = createSchnorrSimulatorInputsNoChecks(publicKeys[currentPositions[j]], order, rand);
							}
							combined = new CryptoDataArray(andData);
						}
						
						else {
							combined = createSchnorrSimulatorInputsNoChecks(publicKeys[currentPositions[0]], order, rand);
						}
						orData[i] = combined;
	
						simulatedChallenges[i] = new BigIntData(new BigInteger(order.bitLength() - 1, rand));
					}
					for(int j = 0; j < k; j++)
					{
						currentPositions[currentPositions.length - 1 - j]++;
						if(currentPositions[currentPositions.length - 1 - j] != n - j)
						{
							for(int l = j; l > 0; l--)
							{
								currentPositions[currentPositions.length - l] = currentPositions[currentPositions.length - 1 - l] + 1;
							}
							break;
						}
					}
					
				}
				orData[nCrk] = new CryptoDataArray(simulatedChallenges);
			}
			else
			{
				orData = new CryptoData[k];
				for(int j = 0; j < k; j++)
				{
					if(privateKeys[j] == null)
						orData[j] = null;
					else
						orData[j] = createSchnorrProverInputsNoChecks(publicKeys[j], privateKeys[j], order, rand);
				}
			}
			return new CryptoDataArray(orData);
		}
		else 
		{
			if(privateKeys[0] == null)
				return null;
			else
				return createSchnorrProverInputsNoChecks(publicKeys[0], privateKeys[0], order, rand);
		}
	}
	public static CryptoData createMultiSigSimulatorDataNaive(int n, int k, CryptoData[] publicKeys, ZKPProtocol p, BigInteger order, SecureRandom rand)  //TODO Should get order from baseEnvironment
	{
		if(n < k || publicKeys.length != n)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		
		if(n != 1)
		{	
			CryptoData[] orData = null; 
			int nCrk = choose(n, k);
			CryptoData[] simulatedChallenges = new CryptoData[nCrk];
			CryptoData[] temp1 = new CryptoData[1];
			if(n > k)
			{
				orData = new CryptoData[nCrk+1];
				int[] currentPositions = new int[k];
				for(int i = 0; i < k; i++)
				{
					currentPositions[i] = i;
				}
				simulatedChallenges[0] = new BigIntData(BigInteger.ZERO);
				for(int i = 0; i < nCrk; i++)
				{
					
					CryptoData combined;
					if(k > 1)
					{
						CryptoData[] andData = new CryptoData[k];
						for(int j = 0; j < k; j++)
						{
							andData[j] = createSchnorrSimulatorInputsNoChecks(publicKeys[currentPositions[j]], order, rand);
						}
						combined = new CryptoDataArray(andData);
					}
					

					else {
						combined = createSchnorrSimulatorInputsNoChecks(publicKeys[currentPositions[0]], order, rand);
					}
					orData[i] = combined;
					
					
					for(int j = 0; j < k; j++)
					{
						currentPositions[currentPositions.length - 1 - j]++;
						if(currentPositions[currentPositions.length - 1 - j] != n - j)
						{
							for(int l = j; l > 0; l--)
							{
								currentPositions[currentPositions.length - l] = currentPositions[currentPositions.length - 1 - l] + 1;
							}
							break;
						}
					}
					if(i != 0) simulatedChallenges[i] = new BigIntData(new BigInteger(order.bitLength() - 1, rand));
					
				}
				orData[nCrk] = new CryptoDataArray(simulatedChallenges);
			}
			else
			{
				orData = new CryptoData[k];
				for(int j = 0; j < k; j++)
				{
					temp1[0] = publicKeys[j];
					orData[j] = createSchnorrSimulatorInputsNoChecks(publicKeys[j], order, rand);
				}
			}
			CryptoData toReturn = new CryptoDataArray(orData);
			return toReturn;
		}
		else {
			CryptoData toReturn = createSchnorrSimulatorInputsNoChecks(publicKeys[0], order, rand);
			return toReturn;
		}
	}
	public static CryptoData createMultiSigVerifierInputsNaive(int n, int k, CryptoData[] publicKeys, ZKPProtocol p)
	{
		if(n < k || publicKeys.length != n)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		if(n != 1)
		{
		
			if(n > k)
			{
				int nCrk = choose(n, k);
				CryptoData[] orData = new CryptoData[nCrk];
				int[] currentPositions = new int[k];
				for(int i = 0; i < k; i++)
				{
					currentPositions[i] = i;
				}
				for(int i = 0; i < nCrk; i++)
				{
					
					CryptoData combined;
					if(k > 1)
					{
						CryptoData[] andData = new CryptoData[k];
						for(int j = 0; j < k; j++)
						{
							andData[j] = createSchnorrVerifierInputsNoChecks(publicKeys[currentPositions[j]]);
						}
						combined = new CryptoDataArray(andData);
					}

					else {
						combined = createSchnorrVerifierInputsNoChecks(publicKeys[i]);
					}
					orData[i] = combined;
					
					for(int j = 0; j < k; j++)
					{
						currentPositions[currentPositions.length - 1 - j]++;
						if(currentPositions[currentPositions.length - 1 - j] != n - j)
						{
							for(int l = j; l > 0; l--)
							{
								currentPositions[currentPositions.length - l] = currentPositions[currentPositions.length - 1 - l] + 1;
							}
							break;
						}
					}
					
				}
				return new CryptoDataArray(orData);

			}
			else
			{
				CryptoData andData[] = new CryptoData[k];
				for(int j = 0; j < k; j++)
				{
					andData[j] = createSchnorrVerifierInputsNoChecks(publicKeys[j]);
				}
				return new CryptoDataArray(andData);
			}
		}
		
		else return createSchnorrVerifierInputsNoChecks(publicKeys[0]);
	}
	
	public static CryptoData createSchnorrProverInputsNoChecks(CryptoData publicInformation, CryptoData secrets, BigInteger order, SecureRandom rand)
	{
		if(secrets.hasNull()) return null;
		CryptoData[] array = new CryptoData[3];
		array[0] = publicInformation;
		BigInteger r;
		do {
			r = new BigInteger(order.bitLength(), rand);
		}while(r.compareTo(order) >= 0);
		array[1] = new BigIntData(r);
		array[2] = secrets;
		
		return new CryptoDataArray(array);
	}
	public static CryptoData createSchnorrSimulatorInputsNoChecks(CryptoData publicInformation, BigInteger order, SecureRandom rand)
	{
		CryptoData[] array = new CryptoData[2];
		array[0] = publicInformation;
		BigInteger r;
		do {
			r = new BigInteger(order.bitLength(), rand);
		}while(r.compareTo(order) >= 0);
		array[1] = new BigIntData(r);
		
		return new CryptoDataArray(array);
	}
	public static CryptoData createSchnorrVerifierInputsNoChecks(CryptoData publicInformation)
	{
		return new CryptoDataArray(new CryptoData[] {publicInformation});
	}
	public static CryptoData createVarianceProverData(CryptoData keyData, BigInteger commitmentKey, ECPoint commitment, BigInteger balance, CryptoData baseEnvironment, boolean claimed, SecureRandom r)
	{
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		BigInteger order = c.getOrder();
		CryptoData[] outer = new CryptoData[3];
		CryptoData[] simulatedChallenges = new CryptoData[2];
		CryptoData[] inner = new CryptoData[2];
		BigInteger simulatedChallenge = new BigInteger(order.bitLength() - 1, r);
		CryptoData comm0 = new ECPointData(commitment);
		CryptoData commB = new ECPointData(commitment.subtract(g.multiply(balance)));
		if(claimed)
		{
			simulatedChallenges[0] = new BigIntData(simulatedChallenge);
			simulatedChallenges[1] = new BigIntData(BigInteger.ZERO);
			
			outer[0] = createSchnorrSimulatorInputsNoChecks(comm0, order, r);
			inner[0] = createSchnorrProverInputsNoChecks(commB,new BigIntData(commitmentKey), order, r);
		}
		else
		{
			simulatedChallenges[0] = new BigIntData(BigInteger.ZERO);
			simulatedChallenges[1] = new BigIntData(simulatedChallenge);
			
			outer[0] = createSchnorrProverInputsNoChecks(comm0, new BigIntData(commitmentKey), order, r);
			inner[0] = createSchnorrSimulatorInputsNoChecks(commB, order, r);
		}
		inner[1] = keyData; 
		outer[2] = new CryptoDataArray(simulatedChallenges);
		outer[1] = new CryptoDataArray(inner);
		return new CryptoDataArray(outer);
	}
	public static CryptoData createVarianceVerifierData(CryptoData keyData, ECPoint commitment, BigInteger balance, CryptoData baseEnvironment)
	{
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		
		CryptoData[] outer = new CryptoData[2];
		CryptoData[] inner = new CryptoData[2];
		
		CryptoData comm0 = new ECPointData(commitment);
		CryptoData commB = new ECPointData(commitment.subtract(g.multiply(balance)));

		outer[0] = createSchnorrVerifierInputsNoChecks(comm0);
		inner[0] = createSchnorrVerifierInputsNoChecks(commB);
		
		inner[1] = keyData;
		outer[1] = new CryptoDataArray(inner);
		return new CryptoDataArray(outer);
	}
	public static CryptoData createVarianceEnvironment(CryptoData keyEnvironment,CryptoData baseEnvironment)
	{
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		ECPoint h = e[1].getECPointData(c);
		CryptoData reverseEnvironment = new CryptoDataArray(new CryptoData[] {new ECCurveData(c, h), new ECPointData(g)});
		CryptoData[] outer = new CryptoData[2];
		CryptoData[] inner = new CryptoData[2];
		inner[0] = reverseEnvironment;
		inner[1] = keyEnvironment;
		outer[0] = reverseEnvironment;
		outer[1] = new CryptoDataArray(inner);
		return new CryptoDataArray(outer);
	}

	public static ZKPProtocol createVarianceMultiSigProof(ZKPProtocol keyProtocol, ZKPProtocol commitmentProtocol) {
		ZKPProtocol[] and = new ZKPProtocol[2];
		ZKPProtocol[] or = new ZKPProtocol[2];
		and[0] = commitmentProtocol;
		and[1] = keyProtocol;
		
		or[0] = commitmentProtocol;
		or[1] = new ZeroKnowledgeAndProver(and);
		return new ZeroKnowledgeOrProver(or);
	}
	
	public static ZKPProtocol createMultiSigProofKeyCount(int n, int k, ZKPProtocol baseProtocol, ZKPProtocol commitmentProtocol) {

		if(n < k)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		if(n == 1) {
			return baseProtocol;
		}
		if(k == 1)
		{
			ZKPProtocol[] proofInner = new ZKPProtocol[n];
			for(int i = 0; i < n; i++){
				proofInner[i] = baseProtocol;
			}
			return new ZeroKnowledgeOrProver(proofInner);
		}
		if(k == n)
		{
			ZKPProtocol[] proofInner = new ZKPProtocol[n];
			for(int i = 0; i < n; i++){
				proofInner[i] = baseProtocol;
			}
			return new ZeroKnowledgeAndProver(proofInner);
		}
		ZKPProtocol[] andOuter = new ZKPProtocol[2];
		ZKPProtocol[] andInner = new ZKPProtocol[2];
		ZKPProtocol[] or = new ZKPProtocol[2];

		andInner[0] = commitmentProtocol;
		andInner[1] = baseProtocol;
		
		or[0] = commitmentProtocol;
		or[1] = new ZeroKnowledgeAndProver(andInner);
		ZKPProtocol oneProof = new ZeroKnowledgeOrProver(or);
		ZKPProtocol[] proof = new ZKPProtocol[n];
		for(int i = 0; i < n; i++) {
			proof[i] = oneProof;
		}
		andOuter[0] = new ZeroKnowledgeOrProver(proof);
		andOuter[1] = commitmentProtocol;
		return new ZeroKnowledgeAndProver(andOuter);
	}
	public static CryptoData createMultiSigEnvironmentKeyCount(int n, int k, CryptoData keyEnvironment, CryptoData commitmentEnvironment)
	{
		if(n < k)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		if(n != 1)
		{
			CryptoData inner;
			if(k == 1 || k == n) {
				inner = keyEnvironment;
			}
			else {
				CryptoData[] andInner = new CryptoData[2];
				CryptoData[] or = new CryptoData[2];
				andInner[0] = commitmentEnvironment;
				andInner[1] = keyEnvironment;
				or[0] = commitmentEnvironment;
				or[1] = new CryptoDataArray(andInner);
				
				inner = new CryptoDataArray(or);
				
			}
			CryptoData[] andOuter = new CryptoData[2];
		
			CryptoData[] env = new CryptoData[n];
			for(int i = 0; i < n; i++) {
				env[i] = inner;
			}
			if(k != n && k != 1) {
				andOuter[0] = new CryptoDataArray(env);
				andOuter[1] = commitmentEnvironment;
				return new CryptoDataArray(andOuter);
			}
			else return new CryptoDataArray(env);
		}
		else return keyEnvironment;
	}
	
	public static CryptoData createMultiSigVerifierInputsKeyCount(int n, int k, CryptoData[] publicKeys, ECPedersenCommitment[] commitments, CryptoData commitmentEnvirionment, ZKPProtocol p)
	{
		if(n < k || publicKeys.length != n)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		ECCurve c = commitmentEnvirionment.getCryptoDataArray()[0].getECCurveData();
		ECPoint g = commitmentEnvirionment.getCryptoDataArray()[0].getECPointData(c);
		if(n != 1)
		{
			CryptoData[] inner = new CryptoData[n];
			for(int i = 0; i < n; i++) {
				inner[i] = createSchnorrVerifierInputsNoChecks(publicKeys[i]);
			}
			if(k == 1 || k == n) {
				return new CryptoDataArray(inner);
			}
			CryptoData[] andOuter = new CryptoData[2];
			CryptoData[] toReturn = new CryptoData[n];
			for(int i = 0; i < n; i++) {
				CryptoData[] andInner = new CryptoData[2];
				CryptoData[] or = new CryptoData[2];
				andInner[0] = new ECPointData(commitments[i].getCommitment(commitmentEnvirionment).subtract(g));
				andInner[1] = inner[i];
				or[0] = new ECPointData(commitments[i].getCommitment(commitmentEnvirionment));
				or[1] = new CryptoDataArray(andInner);
				toReturn[i] = new CryptoDataArray(or);
			}
			andOuter[0] = new CryptoDataArray(toReturn);

			ECPedersenCommitment otherTotalComm = commitments[0];
			for(int i = 1; i < n; i++){
				otherTotalComm = otherTotalComm.multiplyCommitment(commitments[i], commitmentEnvirionment);
			}
			andOuter[1] = createSchnorrVerifierInputsNoChecks(new ECPointData(otherTotalComm.getCommitment(commitmentEnvirionment)));
			return new CryptoDataArray(andOuter);
		}
		
		else return createSchnorrVerifierInputsNoChecks(publicKeys[0]);
	}
	
	public static CryptoData createMultiSigProverDataKeyCount(int n, int k, CryptoData[] publicKeys, CryptoData[] privateKeys, int[] positions, CryptoData baseEnvironment, ECPedersenCommitment[] commitments, BigInteger[] ephemeralKey, BigInteger order, SecureRandom rand)  //TODO Should get order from baseEnvironment
	{
		if(n < k || publicKeys.length != n || privateKeys.length < k || privateKeys.length != positions.length)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		if(n != 1)
		{
			if(k == 1)
			{
				CryptoData[] simulatedChallenges = new CryptoData[n];
				CryptoData[] or = new CryptoData[n + 1];
				for(int i = 0; i < n; i++){
					if(i == positions[0]){
						simulatedChallenges[i] = new BigIntData(BigInteger.ZERO);
						if(privateKeys[0] == null){
							or[i] = null;
						}
						else
							or[i] = createSchnorrProverInputsNoChecks(publicKeys[i], privateKeys[0], order, rand);
					}
					else {
						or[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
						simulatedChallenges[i] = new BigIntData(new BigInteger(255, rand));
					}
				}
				or[n] = new CryptoDataArray(simulatedChallenges);
				return new CryptoDataArray(or);
			}
			if(k == n) {
				CryptoData[] and = new CryptoData[n];
				for(int i = 0; i < n; i++)
				{
					if(privateKeys[i] == null){
						and[i] = null;
					}
					else
						and[i] = createSchnorrProverInputsNoChecks(publicKeys[i], privateKeys[i], order, rand);
				}
				return new CryptoDataArray(and);
			}

			CryptoData[] data = new CryptoData[n];
			int counter = 0;
			CryptoData[] andOuter = new CryptoData[2];
			for(int i = 0; i < n; i++) {
				CryptoData[] andInner = new CryptoData[2];
				CryptoData[] or = new CryptoData[3];
				if(positions[counter] == i)
				{
					andInner[0] = createSchnorrProverInputsNoChecks(new ECPointData(commitments[i].getCommitment(baseEnvironment).subtract(g)), new BigIntData(ephemeralKey[i]), order, rand);
					if(privateKeys[positions[counter]] == null) {
						andInner[1] = null;
					}
					else{
						andInner[1] = createSchnorrProverInputsNoChecks(publicKeys[i], privateKeys[positions[counter]], order, rand);
					}
					or[0] = createSchnorrSimulatorInputsNoChecks(new ECPointData(commitments[i].getCommitment(baseEnvironment)), order, rand);
					or[1] = new CryptoDataArray(andInner);
					or[2] = new CryptoDataArray(new CryptoData[] {new BigIntData(new BigInteger(255, rand)), new BigIntData(BigInteger.ZERO)});
					
					counter++;
				}
				else {
					andInner[0] = createSchnorrSimulatorInputsNoChecks(new ECPointData(commitments[i].getCommitment(baseEnvironment).subtract(g)), order, rand);
					andInner[1] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
					or[0] = createSchnorrProverInputsNoChecks(new ECPointData(commitments[i].getCommitment(baseEnvironment)), new BigIntData(ephemeralKey[i]), order, rand);
					or[1] = new CryptoDataArray(andInner);
					or[2] = new CryptoDataArray(new CryptoData[] {new BigIntData(BigInteger.ZERO),new BigIntData(new BigInteger(255, rand))});
				}
				data[i] = new CryptoDataArray(or);
			}
			andOuter[0] = new CryptoDataArray(data);
			ECPedersenCommitment totalComm = commitments[0];
			BigInteger totalKey = ephemeralKey[0];
			for(int i = 1; i < n; i++){
				totalComm = totalComm.multiplyCommitment(commitments[i], baseEnvironment);
				totalKey = totalKey.add(ephemeralKey[i]);
			}
			andOuter[1] = createSchnorrProverInputsNoChecks(new ECPointData(totalComm.getCommitment(baseEnvironment)), new BigIntData(totalKey), order, rand);
			return new CryptoDataArray(andOuter);
		}
		else 
		{
			if(privateKeys[0] == null)
				return null;
			else
				return createSchnorrProverInputsNoChecks(publicKeys[0], privateKeys[0], order, rand);
		}
	}
	public static CryptoData createMultiSigSimulatorDataKeyCount(int n, int k, CryptoData[] publicKeys, CryptoData baseEnvironment, ECPedersenCommitment[] commitments, BigInteger[] ephemeralKey, BigInteger order, SecureRandom rand)  //TODO Should get order from baseEnvironment
	{
		if(n < k || publicKeys.length != n)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		if(n != 1)
		{
			if(k == 1)
			{
				CryptoData[] simulatedChallenges = new CryptoData[n];
				CryptoData[] data = new CryptoData[n + 1];
				for(int i = 0; i < n; i++){
					data[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
					if(i != 0) simulatedChallenges[i] = new BigIntData(new BigInteger(255, rand));
					else simulatedChallenges[i] = new BigIntData(BigInteger.ZERO);
				}
				data[n] = new CryptoDataArray(simulatedChallenges);
				return new CryptoDataArray(data);
			}
			if(k == n) {
				CryptoData[] data = new CryptoData[n];
				for(int i = 0; i < n; i++)
				{
					data[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
				}
				return new CryptoDataArray(data);
			}

			CryptoData[] data = new CryptoData[n];
			CryptoData[] andOuter = new CryptoData[2];
			for(int i = 0; i < n; i++) {
				CryptoData[] andInner = new CryptoData[2];
				CryptoData[] or = new CryptoData[3];
				andInner[0] = createSchnorrSimulatorInputsNoChecks(new ECPointData(commitments[i].getCommitment(baseEnvironment).subtract(g)), order, rand);
				andInner[1] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
				or[0] = createSchnorrProverInputsNoChecks(new ECPointData(commitments[i].getCommitment(baseEnvironment)), new BigIntData(ephemeralKey[i]), order, rand);
				or[1] = new CryptoDataArray(andInner);
				or[2] = new CryptoDataArray(new CryptoData[] {new BigIntData(BigInteger.ZERO),new BigIntData(new BigInteger(255, rand))});
				data[i] = new CryptoDataArray(or);
			}
			andOuter[0] = new CryptoDataArray(data);

			ECPedersenCommitment totalComm = commitments[0];
			for(int i = 1; i < n; i++){
				totalComm = totalComm.multiplyCommitment(commitments[i], baseEnvironment);
			}
			andOuter[1] = createSchnorrSimulatorInputsNoChecks(new ECPointData(totalComm.getCommitment(baseEnvironment)), order, rand);
			return new CryptoDataArray(andOuter);
		}
		else return createSchnorrSimulatorInputsNoChecks(publicKeys[0], order, rand);
	}
	
	public static ZKPProtocol createMultiSigProofThreshhold(int n, int k, ZKPProtocol keyProtocol, BigInteger challengePrime)
	{
		
		if(n < k || n < 0)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		
		if(n == 1) {
			return keyProtocol;
		}
		ZKPProtocol[] protocols = new ZKPProtocol[n];
		for(int i = 0; i < n; i++) {
			protocols[i] = keyProtocol;
		}
		if(k == 1) {
			return new ZeroKnowledgeOrProver(protocols);
		}
		else if(k == n) {
			return new ZeroKnowledgeAndProver(protocols);
		}
		else {
			return new ZeroKnowledgeThreshhold(protocols, k, challengePrime);
		}
		
	}
	public static CryptoData createMultiSigEnvironmentThreshhold(int n, int k, CryptoData baseEnvironment)
	{
		if(n < k)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		if(n != 1)
		{
			CryptoData[] orSubEnvironment = new CryptoData[n];
			for(int i = 0; i < n; i++) {
				orSubEnvironment[i] = baseEnvironment;
			}
			return new CryptoDataArray(orSubEnvironment);
			
		}
		else return baseEnvironment;
	}
	public static CryptoData createMultiSigProverDataThreshhold(int n, int k, CryptoData[] publicKeys, CryptoData[] privateKeys, int[] positions, CryptoData baseEnvironment, ZKPProtocol p, BigInteger order, SecureRandom rand)  //TODO Should get order from baseEnvironment
	{
		if(n < k || publicKeys.length != n || privateKeys.length < k || privateKeys.length != positions.length)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		CryptoData[] e = baseEnvironment.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		if(n != 1)
		{
			if(k == 1) {
				BigInteger[] simulatedChallenges = new BigInteger[n];
				CryptoData[] inputs = new CryptoData[n+1];
				for(int i = 0; i < n; i++) {
					if(positions[0] == i) {
						if(privateKeys[0] != null)
						{
							inputs[i] = createSchnorrProverInputsNoChecks(publicKeys[i],privateKeys[0], order, rand);
						}
						else inputs[i] = null;
						simulatedChallenges[i] = BigInteger.ZERO;
					}
					else {
						inputs[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
						simulatedChallenges[i] = new BigInteger(order.bitLength()-1, rand);
					}
					
				}
				inputs[n] = new CryptoDataArray(simulatedChallenges);
				return new CryptoDataArray(inputs);
			}
			if(k == n) {
				CryptoData[] inputs = new CryptoData[n];
				for(int i = 0; i < n; i++) {
					if(privateKeys[i] != null)
					{
						inputs[i] = createSchnorrProverInputsNoChecks(publicKeys[i],privateKeys[i], order, rand);
					}
					else inputs[i] = null;
					
				}

				return new CryptoDataArray(inputs);
			}
			int count = 0;
			BigInteger[] simulatedChallenges = new BigInteger[n];
			CryptoData[] inputs = new CryptoData[n+1];
			for(int i = 0; i < n; i++) {
				if(count != positions.length && positions[count] == i) {
					if(privateKeys[count] == null)
						inputs[i] = null;
					else {
						inputs[i] = createSchnorrProverInputsNoChecks(publicKeys[i], privateKeys[count], order, rand);	
					}
					count++;
					simulatedChallenges[i] = BigInteger.ZERO;
				}
				else {
					inputs[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
					simulatedChallenges[i] = new BigInteger(order.bitLength()-1, rand);
				}
			}
			
			inputs[n] = new CryptoDataArray(simulatedChallenges);
			return new CryptoDataArray(inputs);
		}
		else 
		{
			if(privateKeys[0] == null)
				return null;
			else
				return createSchnorrProverInputsNoChecks(publicKeys[0], privateKeys[0], order, rand);
		}
	}
	public static CryptoData createMultiSigSimulatorDataThreshhold(int n, int k, CryptoData[] publicKeys, ZKPProtocol p, BigInteger order, SecureRandom rand, BigInteger challengePrime)  
	{
		if(n < k || publicKeys.length != n)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		
		if(n == 1) return createSchnorrSimulatorInputsNoChecks(publicKeys[0], order, rand);
		if(k == 1) {
			CryptoData[] inputs = new CryptoData[n+1];
			BigInteger[] simulatedChallenge = new BigInteger[n];
			for(int i = 0; i < n; i++) {
				inputs[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
				simulatedChallenge[i] = new BigInteger(order.bitLength()-1, rand);
			}
			simulatedChallenge[0] = BigInteger.ZERO;
			inputs[n] = new CryptoDataArray(simulatedChallenge);
			return new CryptoDataArray(inputs);
		}
		if(k == n) {
			CryptoData[] inputs = new CryptoData[n];
			for(int i = 0; i < n; i++) {
				inputs[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
			}
			return new CryptoDataArray(inputs);
		}
		CryptoData[] inputs = new CryptoData[n+1];
		BigInteger[] coefficients = new BigInteger[n-k];
		for(int i = 0; i < n; i++) {
			inputs[i] = createSchnorrSimulatorInputsNoChecks(publicKeys[i], order, rand);
		}
		for(int i = 0; i < coefficients.length; i++) {
			coefficients[i] = new BigInteger(order.bitLength()-1, rand);
		}
		inputs[n] = new CryptoDataArray(coefficients);
		return new CryptoDataArray(inputs);

	}
	public static CryptoData createMultiSigVerifierInputsThreshhold(int n, int k, CryptoData[] publicKeys, ZKPProtocol p, BigInteger challengePrime)
	{
		if(n < k || publicKeys.length != n)
		{
			throw new ArrayIndexOutOfBoundsException();
		}
		if(n == 1) {
			return createSchnorrVerifierInputsNoChecks(publicKeys[0]);
		}
		CryptoData[] inputs = new CryptoData[n];
		for(int i = 0; i < n; i++) {
			inputs[i] = createSchnorrVerifierInputsNoChecks(publicKeys[i]);
		}
		return new CryptoDataArray(inputs);
	}
}