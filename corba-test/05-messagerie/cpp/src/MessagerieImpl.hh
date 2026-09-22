#ifndef MESSAGERIEIMPL_HH
#define MESSAGERIEIMPL_HH

#include "app.hh"
#include <omnithread.h>
#include <vector>

class MessagerieImpl : public POA_chat::Messagerie
{
    // Les references CORBA des clients abonnes (etape 4 de l'exercice).
    // Ce ne sont PAS des strings : ce sont des objets distants "vivants"
    // sur lesquels on peut appeler nouveauMessage().
    std::vector<chat::Notifiable_var> abonnes;

    // omniORB traite chaque appel entrant dans un thread : deux clients qui
    // appellent envoyer() en meme temps toucheraient le vector en meme temps.
    omni_mutex verrou;

    // L'annuaire, pour retrouver le Moderateur (Java) a chaque message
    CosNaming::NamingContextExt_var nc;

public:
    MessagerieImpl(CosNaming::NamingContextExt_ptr annuaire);
    void envoyer(const char *auteur, const char *contenu);
    void sAbonner(chat::Notifiable_ptr abonne);
};

#endif
