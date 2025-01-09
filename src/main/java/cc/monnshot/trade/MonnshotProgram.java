package cc.monnshot.trade;

import software.sava.core.accounts.PublicKey;

public interface MonnshotProgram {

  PublicKey MOONSHOT = PublicKey.fromBase58Encoded("MoonCVVNZFSYkqNXP6bxHLPL6QQJiMagDL3qcqUQTrG");

  PublicKey DEX_FEE = PublicKey.fromBase58Encoded("3udvfL24waJcLhskRAsStNMoNUvtyXdxrWQz4hgi953N");

  PublicKey HELIO_FEE = PublicKey.fromBase58Encoded("5K5RtTWzzLp4P8Npi84ocf7F1vBsAu29N1irG4iiUnzt");

  PublicKey CONFIG = PublicKey.fromBase58Encoded("36Eru7v11oU5Pfrojyn5oY3nETA1a1iqsw2WUu6afkM9");

}
