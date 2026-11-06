/** Cryptographically unbiased random integer in [0,n). Browser secure context required. */
export function randomIndex(n){
  if(!Number.isSafeInteger(n)||n<1||n>4294967296)throw new RangeError('invalid range');
  const max=Math.floor(4294967296/n)*n;
  const a=new Uint32Array(1);
  do{crypto.getRandomValues(a)}while(a[0]>=max);
  return a[0]%n;
}
export function secureShuffle(ids){
  const a=[...ids];
  for(let i=a.length-1;i>0;i--){
    const j=randomIndex(i+1);
    [a[i],a[j]]=[a[j],a[i]];
  }
  return a;
}
export function pickWithoutReplacement(remaining,idx){
  if(!Number.isInteger(idx)||idx<0||idx>=remaining.length)throw new RangeError('invalid pick index');
  return remaining.splice(idx,1)[0];
}
