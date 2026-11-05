AnyEnchant - n'importe quel enchantement sur n'importe quel objet (Paper 1.21+)

COMMANDES (permission anyenchant.use, op par defaut)
  /enchantany <enchantement> [niveau]             enchante l'objet en main
  /enchantbook <enchantement> [niveau] [quantite]  donne des livres ; clic gauche avec
                                                   le livre sur un objet = enchantement applique
Exemples
  /enchantany riptide 3
  /enchantbook infinity 1
  /enchantbook sharpness 100 5

COMPILER (Java 21 + Maven necessaires)
  mvn package
  -> le plugin est dans target/AnyEnchant.jar
