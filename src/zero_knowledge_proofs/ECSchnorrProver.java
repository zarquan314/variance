package zero_knowledge_proofs;

import java.math.BigInteger;

import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

import zero_knowledge_proofs.CryptoData.CryptoData;
import zero_knowledge_proofs.CryptoData.CryptoDataArray;

public class ECSchnorrProver extends ZKPProtocol {
	 
	 
	 //input format:  [[y, r, x]]

	@Override
	public CryptoData initialComm(CryptoData input, CryptoData environment) {
		ECPoint[] data = new ECPoint[1];
		CryptoData[] e = environment.getCryptoDataArray();
		CryptoData[] i = input.getCryptoDataArray();
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		BigInteger r = i[1].getBigInt();
		data[0] = g.multiply(r);

		CryptoData toReturn = new CryptoDataArray(data);
		return toReturn;
	}

	//input format [y, z]
	@Override
	public CryptoData initialCommSim(CryptoData input, BigInteger challenge, CryptoData environment) {
		ECPoint[] data;
		CryptoData[] i;
		CryptoData[] e;
		ECCurve c = null;
		ECPoint g;
		ECPoint y;
		try{
			data = new ECPoint[1];
			i = input.getCryptoDataArray();
			e = environment.getCryptoDataArray();		//(y, z) 
			c = e[0].getECCurveData();
			g = e[0].getECPointData(c);
			y = i[0].getECPointData(c);
		}catch(NullPointerException e1)
		{
			e1.printStackTrace(); 
			System.out.println(environment);
			System.out.println(c);
			throw new NullPointerException(e1.getMessage());
		}
		BigInteger z = i[1].getBigInt();
		//a = g^z * y^(-c)
		//System.out.printf("c = %s\ninputs = %s\n", challenge.toString(16), input);
		data[0] = g.multiply(z).add(y.multiply(challenge.negate()));
		
		CryptoData toReturn = new CryptoDataArray(data);
		return toReturn;
	}

	@Override
	public CryptoData calcResponse(CryptoData input, BigInteger challenge, CryptoData environment) {
		BigInteger[] array = new BigInteger[1];
		CryptoData[] i = input.getCryptoDataArray();
		CryptoData[] e = environment.getCryptoDataArray();

		BigInteger x = i[2].getBigInt();
		BigInteger r = i[1].getBigInt();
		array[0] = (r.add(x.multiply(challenge))).mod(e[0].getECCurveData().getOrder());
		//System.out.printf("P:\t%s ?= %s\n", ((i[1].modPow(challenge, e[1]).multiply(e[0].modPow(i[2], e[1]))).mod(e[1])), e[0].modPow(array[0], e[1]));
		
		//System.out.printf("P:\tg = %s\nP:\th = %s\nP:\tp = %s\nP:\tr = %s\nP:\tx = %s\nP:\ty = %s\nP:\tz = %s\nP:\tc = %s\n",e[0],e[1],e[1],i[2],i[0],i[1], array[0], challenge);
		return new CryptoDataArray(array);
	}

	@Override
	public CryptoData simulatorGetResponse(CryptoData input) {
		CryptoData[] in = input.getCryptoDataArray();
		BigInteger[] out = new BigInteger[1];
		out[0] = in[1].getBigInt();
		return new CryptoDataArray(out);
	}

	//input format:  [y]

	@Override
	public boolean verifyResponse(CryptoData input, CryptoData initial_comm, CryptoData response, BigInteger challenge,
			CryptoData environment) {
		CryptoData[] e = environment.getCryptoDataArray();
		CryptoData[] resp = response.getCryptoDataArray();
		CryptoData[] i = input.getCryptoDataArray();
		CryptoData[] a_pack = initial_comm.getCryptoDataArray();
		
//		System.out.println(environment);
//		System.out.flush();
		
		ECCurve c = e[0].getECCurveData();
		ECPoint g = e[0].getECPointData(c);
		ECPoint y = i[0].getECPointData(c);
		BigInteger z = resp[0].getBigInt();
		ECPoint a = a_pack[0].getECPointData(c);
		
	//	return (a * y^c) mod p == (g^z) mod p 
		if(!((y.multiply(challenge).add(a))).equals(g.multiply(z))) System.out.printf("V:\t%s ?= %s\n", (y.multiply(challenge).add(a)).normalize(),g.multiply(z).normalize());
		return ((y.multiply(challenge).add(a))).equals(g.multiply(z)) ;
	}
}
