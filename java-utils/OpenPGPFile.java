package net.i2p.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.IllegalArgumentException;
import java.security.Security;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGKey;
import org.bouncycastle.bcpg.DSAPublicBCPGKey;
import org.bouncycastle.bcpg.DSASecretBCPGKey;
import org.bouncycastle.bcpg.ElGamalPublicBCPGKey;
import org.bouncycastle.bcpg.ElGamalSecretBCPGKey;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyPacket;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUtil;

import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;

import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;

import net.i2p.client.I2PSessionException;
import net.i2p.crypto.CryptoConstants;
import net.i2p.data.Base32;
import net.i2p.data.Certificate;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.i2p.data.PrivateKey;
import net.i2p.data.PrivateKeyFile;
import net.i2p.data.PublicKey;
import net.i2p.data.SigningPrivateKey;
import net.i2p.data.SigningPublicKey;
import net.i2p.data.VerifiedDestination;


/**
 * This helper class reads and writes Destinations in the OpenPGP Transferable
 * Public Key and Transferable Secret Key formats, as defined in RFC 4880
 * (see https://tools.ietf.org/html/rfc4880 for specifications).
 *
 * @author str4d
 */
public class OpenPGPFile {
    public static void main(String args[]) {
        Security.addProvider(new BouncyCastleProvider());
        PGPUtil.setDefaultProvider("BC");
        if (args.length == 0 || args[0].equals("-?")) {
            System.err.println("Syntax: OpenPGPFile [options] <command>");
            System.err.println("");
            System.err.println("Commands:");
            System.err.println(" -e, --export <eepPriv.dat> <pgpFile>");
            System.err.println(" -i, --import <pgpFile> <eepPriv.dat>");
            System.err.println("");
            System.err.println("Options:");
            System.err.println(" -a, --armor");
            System.err.println(" -f, --force-write");
            System.err.println(" -I, --identity    <identity>");
            return;
        }
        // Parse options
        boolean armorOutput = false;
        boolean forceWrite = false;
        String identity = "";
        int numOpts = 0;
        while(numOpts < args.length) {
            if (args[numOpts].equals("-a") || args[numOpts].equals("--armor")) {
                armorOutput = true;
            } else if (args[numOpts].equals("-f") || args[numOpts].equals("--force-write")) {
                forceWrite = true;
            } else if (args[numOpts].equals("-I") || args[numOpts].equals("--identity")) {
                identity = args[++numOpts];
            } else {
                // No more options
                break;
            }
            numOpts++;
        }
        // Parse commands
        OpenPGPFile opf = null;
        try {
            if (args[numOpts].equals("-e") || args[numOpts].equals("--export")) {
                if (args.length-numOpts < 3) {
                    System.err.println("Usage: OpenPGPFile "+args[numOpts]+" <eepPriv.dat> <pgpFile> [pubFile]");
                    return;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("GPG passphrase to encrypt with: ");
                char[] passPhrase = br.readLine().toCharArray();
                opf = new OpenPGPFile();
                opf.readPrivateKeyFile(new File(args[numOpts+1]));
                opf.exportKeys();
                if (identity.length() > 0)
                    opf.addIdentity(identity);
                opf.writeOpenPGPFile(new File(args[numOpts+2]), passPhrase, armorOutput, forceWrite, (args.length-numOpts>3 ? new File(args[numOpts+3]) : null));
            } else if (args[numOpts].equals("-i") || args[numOpts].equals("--import")) {
                if (args.length-numOpts < 3) {
                    System.err.println("Usage: OpenPGPFile "+args[numOpts]+" <pgpFile> <eepPriv.dat>");
                    return;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("GPG passphrase to decrypt with: ");
                char[] passPhrase = br.readLine().toCharArray();
                opf = new OpenPGPFile();
                opf.readOpenPGPFile(new File(args[numOpts+1]), passPhrase);
                opf.importKeys();
                opf.writePrivateKeyFile(new File(args[numOpts+2]), forceWrite);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public OpenPGPFile() {
        this.dest = null;
        this.privKey = null;
        this.signingPrivKey = null;
        this.lastMod = null;
        this.pgpTopKeyPair = null;
        this.pgpSubKeyPairs = new ArrayList<PGPKeyPair>();
        this.identities = new ArrayList<String>();
    }

    /**
     * Read in I2P keys from a file.
     */
    public void readPrivateKeyFile(File pkFile) throws I2PSessionException, IOException, DataFormatException {
        if (pkFile.exists())
            this.lastMod = new Date(pkFile.lastModified());
        PrivateKeyFile pkf = new PrivateKeyFile(pkFile);
        this.dest = pkf.getDestination();
        this.privKey = pkf.getPrivKey();
        this.signingPrivKey = pkf.getSigningPrivKey();
    }

    /**
     * Write out I2P keys to a file.
     */
    public void writePrivateKeyFile(File pkFile, boolean forceWrite) throws IOException, DataFormatException {
        if(!pkFile.exists() || forceWrite) {
            PrivateKeyFile pkf = new PrivateKeyFile(pkFile, this.dest, this.privKey, this.signingPrivKey);
            pkf.write();
            pkFile.setLastModified(this.lastMod.getTime());
        }
    }

    /**
     * Read in OpenPGP keys from a file.
     */
    public void readOpenPGPFile(File pgpFile, char[] passPhrase) throws FileNotFoundException, IOException, PGPException {
        PGPSecretKeyRing pgpKeys = new PGPSecretKeyRing(
            PGPUtil.getDecoderStream(new FileInputStream(pgpFile)),
            new JcaKeyFingerprintCalculator());
        Iterator it = pgpKeys.getSecretKeys();

        PGPSecretKey pgpTopSecret = (PGPSecretKey)it.next();
        this.pgpTopKeyPair = new PGPKeyPair(
            pgpTopSecret.getPublicKey(),
            pgpTopSecret.extractPrivateKey(
                new JcePBESecretKeyDecryptorBuilder().build(passPhrase)));

        while (it.hasNext()) {
            PGPSecretKey pgpSubSecret = (PGPSecretKey)it.next();
            this.pgpSubKeyPairs.add(new PGPKeyPair(
                pgpSubSecret.getPublicKey(),
                pgpSubSecret.extractPrivateKey(
                    new JcePBESecretKeyDecryptorBuilder().build(passPhrase))));
        }

        this.identities.clear();
        Iterator uids = pgpTopSecret.getUserIDs();
        while(uids.hasNext())
            this.identities.add((String)uids.next());
    }

    /**
     * Write out OpenPGP keys to a file.
     */
    public void writeOpenPGPFile(File pgpFile, char[] passPhrase, boolean armor, boolean forceWrite) throws IOException, PGPException {
        writeOpenPGPFile(pgpFile, passPhrase, armor, forceWrite, null);
    }

    /**
     * Write out OpenPGP keys to a file.
     */
    public void writeOpenPGPFile(File pgpFile, char[] passPhrase, boolean armor, boolean forceWrite, File pubFile) throws IOException, PGPException {
        OutputStream secretOut = null;
        OutputStream publicOut = null;
        if(!pgpFile.exists() || forceWrite) {
            try {
                secretOut = new FileOutputStream(pgpFile);
                if (armor) {
                    secretOut = new ArmoredOutputStream(secretOut);
                }
                // Set up the PGP keyring
                PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
                PGPContentSignerBuilder certSigBuilder = new JcaPGPContentSignerBuilder(this.pgpTopKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1);
                if (this.identities.size() > 1) {
                    PGPSignatureGenerator sGen;
                    try {
                        sGen = new PGPSignatureGenerator(certSigBuilder);
                    } catch (Exception e) {
                        throw new PGPException("creating signature generator: " + e, e);
                    }
                    sGen.init(PGPSignature.DEFAULT_CERTIFICATION, this.pgpTopKeyPair.getPrivateKey());
                    sGen.setHashedSubpackets(null);
                    sGen.setUnhashedSubpackets(null);
                    PGPPublicKey pubKey = this.pgpTopKeyPair.getPublicKey();
                    for (int i = 1; i < this.identities.size(); i++) {
                        try {
                            PGPSignature certification = sGen.generateCertification(this.identities.get(i), pubKey);
                            pubKey = PGPPublicKey.addCertification(pubKey, this.identities.get(i), certification);
                        } catch (Exception e) {
                            throw new PGPException("exception doing certification: " + e, e);
                        }
                    }
                    this.pgpTopKeyPair = new PGPKeyPair(pubKey, this.pgpTopKeyPair.getPrivateKey());
                }
                PGPKeyRingGenerator pgpGen = new PGPKeyRingGenerator(
                    PGPSignature.POSITIVE_CERTIFICATION,
                    this.pgpTopKeyPair,
                    this.identities.get(0),
                    sha1Calc,
                    null,
                    null,
                    certSigBuilder,
                    new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5, sha1Calc).build(passPhrase));
                for (int i = 0; i < this.pgpSubKeyPairs.size(); i++) {
                    pgpGen.addSubKey(this.pgpSubKeyPairs.get(i));
                }
                // Write out the secret keyring
                pgpGen.generateSecretKeyRing().encode(secretOut);
                secretOut.flush();
                // If a public file is supplied, write out the public keyring as well
                if (pubFile != null && (!pubFile.exists() || forceWrite)) {
                    publicOut = new FileOutputStream(pubFile);
                    if (armor) {
                        publicOut = new ArmoredOutputStream(publicOut);
                    }
                    pgpGen.generatePublicKeyRing().encode(publicOut);
                    publicOut.flush();
                }
            } finally {
                if (secretOut != null) {
                    try { secretOut.close(); } catch (IOException ioe) {}
                }
                if (publicOut != null) {
                    try { publicOut.close(); } catch (IOException ioe) {}
                }
            }
        }
    }

    /**
     * Export the I2P key representations to the OpenPGP key representations.
     * This will silently overwrite the current contents of the OpenPGP key
     * representations.
     */
    public void exportKeys() throws PGPException {
        BCPGKey sigPubKey = new DSAPublicBCPGKey(
            CryptoConstants.dsap,
            CryptoConstants.dsaq,
            CryptoConstants.dsag,
            new NativeBigInteger(1, this.dest.getSigningPublicKey().getData()));
        BCPGKey sigPrivKey = new DSASecretBCPGKey(
            new NativeBigInteger(1, this.signingPrivKey.getData()));
        BCPGKey encPubKey = new ElGamalPublicBCPGKey(
            CryptoConstants.elgp,
            CryptoConstants.elgg,
            new NativeBigInteger(1, this.dest.getPublicKey().getData()));
        BCPGKey encPrivKey = new ElGamalSecretBCPGKey(
            new NativeBigInteger(1, this.privKey.getData()));

        PGPPublicKey pgpSigPubKey = new PGPPublicKey(
            new PublicKeyPacket(PublicKeyAlgorithmTags.DSA, this.lastMod, sigPubKey),
            new JcaKeyFingerprintCalculator());
        this.pgpTopKeyPair = new PGPKeyPair(pgpSigPubKey, new PGPPrivateKey(
            pgpSigPubKey.getKeyID(),
            pgpSigPubKey.getPublicKeyPacket(),
            sigPrivKey));

        PGPPublicKey pgpEncPubKey = new PGPPublicKey(
            new PublicKeyPacket(PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT, this.lastMod, encPubKey),
            new JcaKeyFingerprintCalculator());
        this.pgpSubKeyPairs.add(new PGPKeyPair(pgpEncPubKey, new PGPPrivateKey(
            pgpEncPubKey.getKeyID(),
            pgpEncPubKey.getPublicKeyPacket(),
            encPrivKey)));

        this.identities.clear();
        this.identities.add(Base32.encode(this.dest.calculateHash().getData()) + ".b32.i2p");
    }

    /**
     * Import the OpenPGP key representations to the I2P key representations.
     * This will silently overwrite the current contents of the I2P key
     * representations. If any of the cryptographic constants in the OpenPGP
     * keys do not match the I2P constants, an IllegalArgumentException is
     * thrown.
     */
    public void importKeys() throws IllegalArgumentException {
        DSAPublicBCPGKey sigPubKey = (DSAPublicBCPGKey)pgpTopKeyPair.getPublicKey().getPublicKeyPacket().getKey();
        DSASecretBCPGKey sigPrivKey = (DSASecretBCPGKey)pgpTopKeyPair.getPrivateKey().getPrivateKeyDataPacket();
        ElGamalPublicBCPGKey encPubKey = (ElGamalPublicBCPGKey)pgpSubKeyPairs.get(0).getPublicKey().getPublicKeyPacket().getKey();
        ElGamalSecretBCPGKey encPrivKey = (ElGamalSecretBCPGKey)pgpSubKeyPairs.get(0).getPrivateKey().getPrivateKeyDataPacket();

        // Verify the cryptographic constants
        if (!CryptoConstants.dsap.equals(sigPubKey.getP()) ||
            !CryptoConstants.dsaq.equals(sigPubKey.getQ()) ||
            !CryptoConstants.dsag.equals(sigPubKey.getG()) ||
            !CryptoConstants.elgp.equals(encPubKey.getP()) ||
            !CryptoConstants.elgg.equals(encPubKey.getG()))
            throw new IllegalArgumentException("Cryptographic constants do not match");

        // BigInteger.toByteArray returns SIGNED integers, but since they're
        // positive, signed two's complement is the same as unsigned
        byte[] pubKeyData = encPubKey.getY().toByteArray();
        byte[] privKeyData = encPrivKey.getX().toByteArray();
        byte[] signingPubKeyData = sigPubKey.getY().toByteArray();
        byte[] signingPrivKeyData = sigPrivKey.getX().toByteArray();

        PublicKey pubKey = new PublicKey();
        pubKey.setData(padBuffer(pubKeyData, PublicKey.KEYSIZE_BYTES));

        SigningPublicKey signingPubKey = new SigningPublicKey();
        signingPubKey.setData(padBuffer(signingPubKeyData, SigningPublicKey.KEYSIZE_BYTES));

        this.dest = new VerifiedDestination();
        this.dest.setPublicKey(pubKey);
        this.dest.setSigningPublicKey(signingPubKey);
        this.dest.setCertificate(Certificate.NULL_CERT);

        this.privKey = new PrivateKey();
        this.privKey.setData(padBuffer(privKeyData, PrivateKey.KEYSIZE_BYTES));

        this.signingPrivKey = new SigningPrivateKey();
        this.signingPrivKey.setData(padBuffer(signingPrivKeyData, SigningPrivateKey.KEYSIZE_BYTES));

        this.lastMod = pgpTopKeyPair.getPublicKey().getCreationTime();
    }

    /**
     * Adds an identity to the list for this keyring. The identities list is
     * cleared when the OpenPGP representations are changed, so this must be
     * called afterwards.
     */
    public void addIdentity(String identity) {
        if (!this.identities.contains(identity))
            this.identities.add(identity);
    }

    /**
     * Pad the buffer w/ leading 0s or trim off leading bits so the result is the
     * given length.  
     */
    private final static byte[] padBuffer(byte src[], int length) {
        byte buf[] = new byte[length];

        if (src.length > buf.length) // extra bits, chop leading bits
            System.arraycopy(src, src.length - buf.length, buf, 0, buf.length);
        else if (src.length < buf.length) // short bits, padd w/ 0s
            System.arraycopy(src, 0, buf, buf.length - src.length, src.length);
        else
            // eq
            System.arraycopy(src, 0, buf, 0, buf.length);

        return buf;
    }


    // I2P key representations
    private Destination dest;
    private PrivateKey privKey;
    private SigningPrivateKey signingPrivKey;
    private Date lastMod;
    // OpenPGP key representations
    private PGPKeyPair pgpTopKeyPair;
    private List<PGPKeyPair> pgpSubKeyPairs;
    private List<String> identities;
}
