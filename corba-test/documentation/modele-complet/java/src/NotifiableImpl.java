import modele.NotifiablePOA;

// Callback : C++ appelle cette methode, Java ne l'appelle jamais lui-meme
public class NotifiableImpl extends NotifiablePOA {
    public void notifier(String evenement) {
        System.out.println("[Java] <<< Notification : " + evenement);
    }
}
