import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import {readBytes} from './bytes'
export async function loadModel(key,signal,progress,pending){
  const buffer=await (pending || readBytes(key,signal,progress))
  signal.throwIfAborted()
  return (await new GLTFLoader().parseAsync(buffer,'')).scene
}
