import chat.NotifiablePOA;

// Le servant COTE CLIENT : c'est cette methode que le serveur C++ appelle
// (callback) a chaque nouveau message. Java ne l'appelle jamais lui-meme.
public class NotifiableImpl extends NotifiablePOA {
    private final String moi;

    public NotifiableImpl(String moi) {
        this.moi = moi;
    }

    public void nouveauMessage(String auteur, String contenu) {
        if (auteur.equals(moi)) {
            System.out.println("[Java] (accuse de reception) mon message a ete diffuse");
        } else {
            System.out.println("[Java] >>> Notification : " + auteur + " dit : " + contenu);
        }
    }
}
